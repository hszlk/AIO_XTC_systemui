#requires -Version 5.1
<#
.SYNOPSIS
    Stage the jadx-decoded resource tree into a build-local, aapt2-friendly copy.

.DESCRIPTION
    nx\resources\res is READ-ONLY input (never modified). This script produces
    build\obj\res-stage, which is what aapt2 compiles. All transforms are
    deterministic and driven by res\..\respatch\patches.json; every transform
    asserts a minimum hit count so an upstream decode change can never silently
    produce a different APK. The applied list is written to build\res-fixes.log.

    Transform order:
      1. drop res\values\public.xml  (decoded <public> id table; aapt2 owns ids)
      2. demote *.9.png -> *.png      (aapt2 stripped the 1px nine-patch border)
      3. literal patches              (exact-count string replacements)
      4. typed-value rules            (<item name="attr">raw number</item> -> symbolic)
      5. drop-item rules              (unresolvable private framework style attrs)
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ResSrc,
    [Parameter(Mandatory = $true)][string]$StageDir,
    [Parameter(Mandatory = $true)][string]$PatchFile,
    [Parameter(Mandatory = $true)][string]$LogFile
)

$ErrorActionPreference = 'Stop'
$script:log = New-Object System.Collections.Generic.List[string]

function Write-Log([string]$msg) {
    $script:log.Add($msg)
    Write-Host "    $msg"
}

# ---------------------------------------------------------------- 0. clean copy
if (Test-Path $StageDir) { Remove-Item $StageDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null

# robocopy is far faster than Copy-Item on a 900-file tree; exit codes 0-7 are success.
robocopy $ResSrc $StageDir /MIR /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy failed with exit code $LASTEXITCODE" }
$LASTEXITCODE = 0
Write-Log "staged $ResSrc -> $StageDir"

# ------------------------------------------------- 1. drop values/public.xml
$publicXml = Join-Path $StageDir 'values\public.xml'
if (-not (Test-Path $publicXml)) { throw 'expected values\public.xml to exist in the decoded tree' }
Remove-Item $publicXml -Force
Write-Log 'FIX-1  removed values\public.xml (decoded <public> id table; aapt2 owns ids)'

# ----------------------------------------- 2. demote nine-patch PNGs to bitmaps
$renamed = 0
Get-ChildItem $StageDir -Recurse -File -Filter '*.9.png' | ForEach-Object {
    $target = Join-Path $_.DirectoryName ($_.Name -replace '\.9\.png$', '.png')
    if ($target -eq $_.FullName) { throw "nine-patch rename failed for $($_.FullName)" }
    if (Test-Path $target) { throw "nine-patch rename collision: $($_.FullName)" }
    Move-Item $_.FullName $target
    $renamed++
}
Write-Log "FIX-2  demoted $renamed nine-patch PNG(s) to plain bitmaps (aapt2 crunch stripped their 1px borders)"

# ----------------------------------------------------- 3. data driven patches
if (-not (Test-Path $PatchFile)) { throw "patch file not found: $PatchFile" }
$spec = Get-Content -Raw -Encoding UTF8 $PatchFile | ConvertFrom-Json

function Get-Prop($obj, $name) {
    if ($obj.PSObject.Properties.Name -contains $name) { return $obj.$name }
    return 0
}

# --- 3a. literal patches: exact string replacement, exact count asserted
foreach ($p in $spec.literalPatches) {
    $path = Join-Path $StageDir $p.file
    if (-not (Test-Path $path)) { throw "PATCH target missing: $($p.file)" }
    $text = [System.IO.File]::ReadAllText($path)
    $hits = ([regex]::Matches($text, [regex]::Escape($p.find))).Count
    $want = [int](Get-Prop $p 'count')
    if ($hits -ne $want) {
        throw "PATCH not applicable: $($p.file) expected $want hit(s) of '$($p.find)', found $hits"
    }
    [System.IO.File]::WriteAllText($path, $text.Replace($p.find, $p.replace))
    Write-Log "FIX-3a $($p.file): '$($p.find)' -> '$($p.replace)'  [$($p.reason)]"
}

# --- 3b. typed-value rules: raw numbers back to their symbolic aapt2 form
foreach ($r in $spec.typedValueRules) {
    $pattern = '<item name="' + [regex]::Escape($r.attr) + '">\s*' + [regex]::Escape($r.value) + '\s*</item>'
    $replacement = '<item name="' + $r.attr + '">' + $r.symbolic + '</item>'
    # '$' is special in a .NET regex replacement string; the symbolic values never
    # contain it, but escape defensively so a future rule cannot corrupt the output.
    $rx = [regex]::new($pattern)
    $safeReplacement = $replacement.Replace('$', '$$')
    $hits = 0
    foreach ($f in Get-ChildItem $StageDir -Recurse -File -Filter '*.xml') {
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $n = $rx.Matches($text).Count
        if ($n -gt 0) {
            [System.IO.File]::WriteAllText($f.FullName, $rx.Replace($text, $safeReplacement))
            $hits += $n
        }
    }
    $want = [int](Get-Prop $r 'min')
    if ($hits -lt $want) {
        throw "TYPED RULE under-applied: $($r.attr)='$($r.value)' expected >= $want, found $hits"
    }
    Write-Log "FIX-3b $($r.attr)='$($r.value)' -> '$($r.symbolic)'  ($hits hit(s))  [$($r.reason)]"
}

# --- 3c. drop-item rules: remove style items that reference private framework attrs
foreach ($r in $spec.dropItemRules) {
    $rx = [regex]$r.pattern
    $hits = 0
    foreach ($f in Get-ChildItem $StageDir -Recurse -File -Filter '*.xml') {
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $n = $rx.Matches($text).Count
        if ($n -gt 0) {
            [System.IO.File]::WriteAllText($f.FullName, $rx.Replace($text, ''))
            $hits += $n
        }
    }
    $want = [int](Get-Prop $r 'min')
    if ($hits -lt $want) {
        throw "DROP RULE under-applied: '$($r.pattern)' expected >= $want, found $hits"
    }
    Write-Log "FIX-3c dropped $hits item(s) matching '$($r.pattern)'  [$($r.reason)]"
}

# ------------------------------------------------------------------- 4. report
$count = (Get-ChildItem $StageDir -Recurse -File).Count
Write-Log "stage complete: $count resource file(s)"

$dir = Split-Path -Parent $LogFile
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
$script:log | Set-Content -Encoding UTF8 $LogFile
