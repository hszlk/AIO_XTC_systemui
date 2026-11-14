#requires -Version 5.1
<#
.SYNOPSIS
    Static hidden-API guard for the framework-only XTCSystemUI rebuild.

.DESCRIPTION
    The rebuild must run on the stock device framework with nothing but the
    public SDK. Compiling against the public platforms\android-30\android.jar
    already rejects a *statically referenced* @hide symbol (javac cannot resolve
    it), so this script is an independent second gate:

      source scan  -- every occurrence of a hidden type is classified:
                      * plain code reference  -> FAIL (would not even compile)
                      * inside a string literal -> WARN, i.e. a deliberate
                        Class.forName() reflection bridge such as
                        "android.os.SystemProperties" in SystemUIApplication
      dex scan     -- dexdump -d the produced classes.dex and look for hidden
                      type *descriptors* (Landroid/os/SystemProperties;). String
                      constants are not descriptors, so reflection bridges are
                      correctly ignored here.

    Reflection bridges are reported but do not fail the build; they are recorded
    in docs/BUILD.md so the platform-signature / hidden-API-exemption
    requirement stays visible.

    Exit code: 0 = clean or warnings only, 1 = a statically referenced hidden API.
#>
[CmdletBinding()]
param(
    [string[]]$SourceDir = @(),
    [Parameter(Mandatory = $true)][string]$DexFile
)

$ErrorActionPreference = 'Continue'
$BuildTools = 'C:\Users\10413\AppData\Local\Android\Sdk\build-tools\30.0.3'
$DexDump = Join-Path $BuildTools 'dexdump.exe'

# @hide framework classes/types that SystemUI-derived code traditionally reaches
# for. None of them resolve against the public SDK.
$HiddenTypes = @(
    'android.os.SystemProperties',
    'android.app.ActivityThread',
    'android.os.ServiceManager',
    'android.os.ServiceManagerNative',
    'android.os.SystemService',
    'android.view.WindowManagerGlobal',
    'android.util.Slog',
    'android.util.TimingsTraceLog',
    'android.app.StatusBarManager'
)
$HiddenPrefixes = @('com.android.internal.', 'android.internal.')

$failures = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$sourceFiles = 0

function Test-InsideStringLiteral([string]$Text, [int]$Index) {
    # odd number of double quotes before the match => the match sits in a literal
    $before = $Text.Substring(0, $Index)
    $quotes = ($before.ToCharArray() | Where-Object { $_ -eq '"' }).Count
    return (($quotes % 2) -eq 1)
}

# ---------------------------------------------------------------- source scan
foreach ($dir in $SourceDir) {
    if (-not (Test-Path $dir)) { Write-Output "   source dir missing, skipped: $dir"; continue }
    foreach ($f in Get-ChildItem $dir -Recurse -File -Filter '*.java') {
        $sourceFiles++
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $rel = $f.FullName.Substring($dir.Length).TrimStart('\', '/')
        foreach ($t in $HiddenTypes) {
            foreach ($m in [regex]::Matches($text, [regex]::Escape($t))) {
                $line = ($text.Substring(0, $m.Index) -split "`n").Count
                if (Test-InsideStringLiteral $text $m.Index) {
                    $warnings.Add("reflection bridge: $rel`:$line -> `"$t`"")
                }
                else {
                    $failures.Add("static hidden-API reference: $rel`:$line -> $t")
                }
            }
        }
        foreach ($p in $HiddenPrefixes) {
            foreach ($m in [regex]::Matches($text, [regex]::Escape($p))) {
                $line = ($text.Substring(0, $m.Index) -split "`n").Count
                if (Test-InsideStringLiteral $text $m.Index) {
                    $warnings.Add("reflection bridge: $rel`:$line -> `"$p*`"")
                }
                else {
                    $failures.Add("static hidden-API reference: $rel`:$line -> $p*")
                }
            }
        }
    }
}
Write-Output "   source scan : $sourceFiles Java file(s) from $($SourceDir.Count) dir(s)"

# ------------------------------------------------------------------- dex scan
if (Test-Path $DexFile) {
    if (-not (Test-Path $DexDump)) { Write-Output '   dex scan    : skipped (dexdump.exe not found)' }
    else {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $dump = @(& $DexDump -d $DexFile 2>&1 | ForEach-Object { "$_" })
        $ErrorActionPreference = $prev
        foreach ($t in $HiddenTypes) {
            $desc = 'L' + $t.Replace('.', '/') + ';'
            $hit = @($dump | Where-Object { $_.Contains($desc) })
            if ($hit.Count -gt 0) { $failures.Add("dex type reference: $desc ($($hit.Count) line(s))") }
        }
        foreach ($p in $HiddenPrefixes) {
            $desc = 'L' + $p.Replace('.', '/')
            $hit = @($dump | Where-Object { $_.Contains($desc) })
            if ($hit.Count -gt 0) { $failures.Add("dex type reference: $desc* ($($hit.Count) line(s))") }
        }
        Write-Output "   dex scan    : $(Split-Path -Leaf $DexFile) ($($dump.Count) dexdump line(s))"
    }
}
else {
    Write-Output "   dex scan    : skipped ($DexFile not found)"
}

foreach ($w in $warnings) { Write-Output "   WARN  $w" }
if ($warnings.Count -gt 0) {
    Write-Output "   note        : $($warnings.Count) reflection bridge(s) -- allowed, but they need a platform-signed/system-image install to survive hidden-API enforcement"
}

if ($failures.Count -eq 0) {
    Write-Output "   RESULT      : PASS -- no statically referenced hidden API ($($warnings.Count) reflection bridge(s))"
    exit 0
}
Write-Output "   RESULT      : FAIL -- $($failures.Count) hidden-API violation(s)"
$failures | Select-Object -First 20 | ForEach-Object { Write-Output "       $_" }
exit 1
