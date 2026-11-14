#requires -Version 5.1
<#
.SYNOPSIS
    Verify the XTCSystemUI rebuild variants against the vendor APK.

.DESCRIPTION
    Runs one report per variant. Every check is independent and the script exits
    1 if any check fails.

    PARITY variant (build\XTCSystemUI-signed.apk) -- information parity:
      1. aapt2 dump badging identical to the vendor APK
      2. aapt2 dump resources identical (resources.arsc untouched)
      3. sha256 of resources.arsc and of the binary AndroidManifest.xml identical
      4. zip entry set == vendor entries + classes.dex
      5. packaging: zipalign -c, arsc STORED, per-entry compression method,
         all entry CRC-32 valid, classes.dex parses
      6. signature: apksigner verify (device path + explicit v1 pre-v2 path)
      7. all 16 manifest component classes present in the shipped dex
      8. no-cloud gate: residue limited to the 4 audited no-op placeholders
         (explicit whitelist, listed individually)

    STRICT variant (build\XTCSystemUI-signed-strict.apk) -- cloud removed:
      1. badging delta == only the removed cloud permissions/receiver summary
      2. xmltree == vendor xmltree minus exactly the 6 cloud declarations
      3. resources.arsc still identical to the vendor APK
      4. zip entry set == vendor entries + classes.dex
      5. packaging (same checks as parity)
      6. signature (same checks as parity)
      7. 12 remaining component classes present, 4 removed placeholders absent
      8. no-cloud gate: ZERO residue (no whitelist at all)

.PARAMETER OriginalApk
    The vendor APK every variant is compared against.

.PARAMETER SignedApk
    Parity variant APK (required when -Variant is parity or both).

.PARAMETER StrictApk
    Strict variant APK (required when -Variant is strict or both).

.PARAMETER Variant
    parity (default) | strict | both.

.PARAMETER SkipNoCloudScan
    Skip check group 8 (not recommended: it is the gate that proves no cloud /
    network residue is reachable).
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OriginalApk,
    [string]$SignedApk,
    [string]$StrictApk,
    [ValidateSet('parity', 'strict', 'both')][string]$Variant = 'parity',
    [switch]$SkipNoCloudScan
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

$RebuildRoot = Split-Path -Parent $PSScriptRoot
$BuildDir = Join-Path $RebuildRoot 'build'
$SdkRoot = 'C:\Users\10413\AppData\Local\Android\Sdk'
$BuildTools = Join-Path $SdkRoot 'build-tools\30.0.3'
$Aapt2 = Join-Path $BuildTools 'aapt2.exe'
$Zipalign = Join-Path $BuildTools 'zipalign.exe'
$DexDump = Join-Path $BuildTools 'dexdump.exe'
# apksigner.bat / d8.bat in this SDK are broken (missing find_java.bat), so the
# jars are invoked through java.exe directly -- see docs\BUILD.md.
$JavaExe = (Get-Command java -ErrorAction Stop).Source
$ApkSignerJar = Join-Path $BuildTools 'lib\apksigner.jar'
$ZipCheckScript = Join-Path $PSScriptRoot 'zip_check.py'
$NoCloudGateScript = Join-Path $PSScriptRoot 'nocloud_gate.py'
$CloudChecker = 'D:\DEBUG\apk\sysui\analysis\cloud-control\tools\check_nocloud.py'
$PyExe = (Get-Command py -ErrorAction SilentlyContinue).Source
if (-not $PyExe) { $PyExe = (Get-Command python -ErrorAction SilentlyContinue).Source }
$MaxApkBytes = 3MB

# The 6 declarations the strict variant strips (kept in sync with
# analysis\cloud-control\tools\cloud-targets.json).
$CloudTargets = @(
    'com.xtc.bigdata.collector.exception.CrashIntentService',
    'com.xtc.bigdata.monitor.receiver.MonitorStartReceiver',
    'com.xtc.xtcoco.CodeCoverageReceiver',
    'com.xtc.dns.provider.HttpDnsContentProvider',
    'com.xtc.http.bigdata.provider.permission.READ_CONTENT',
    'com.xtc.http.bigdata.provider.permission.WRITE_CONTENT'
)
$RemovedPlaceholderClasses = @(
    'Lcom/xtc/bigdata/collector/exception/CrashIntentService;',
    'Lcom/xtc/bigdata/monitor/receiver/MonitorStartReceiver;',
    'Lcom/xtc/xtcoco/CodeCoverageReceiver;',
    'Lcom/xtc/dns/provider/HttpDnsContentProvider;'
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

$runParity = ($Variant -eq 'parity' -or $Variant -eq 'both')
$runStrict = ($Variant -eq 'strict' -or $Variant -eq 'both')
if ($runParity -and -not $SignedApk) { throw '-Variant parity/both needs -SignedApk' }
if ($runStrict -and -not $StrictApk) { throw '-Variant strict/both needs -StrictApk' }
$ReportPath = if ($Variant -eq 'strict') { Join-Path $BuildDir 'verify-report-strict.txt' }
elseif ($Variant -eq 'both') { Join-Path $BuildDir 'verify-report-both.txt' }
else { Join-Path $BuildDir 'verify-report.txt' }

$script:Report = New-Object System.Collections.Generic.List[string]
$script:Failures = 0
$script:Warnings = 0

function Write-Line([string]$text) {
    $script:Report.Add($text)
    Write-Host $text
}

function Report-Check([string]$name, [bool]$ok, [string[]]$details = @()) {
    $tag = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Line ("[{0}] {1}" -f $tag, $name)
    foreach ($d in $details) { if ($null -ne $d) { Write-Line "       $d" } }
    if (-not $ok) { $script:Failures++ }
}

function Report-Warn([string]$name, [string[]]$details = @()) {
    $script:Warnings++
    Write-Line ("[WARN] {0}" -f $name)
    foreach ($d in $details) { if ($null -ne $d) { Write-Line "       $d" } }
}

function Invoke-Capture([string]$Exe, [string[]]$Arguments) {
    $prevEA = $ErrorActionPreference
    $prevEnc = [Console]::OutputEncoding
    $ErrorActionPreference = 'Continue'
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $env:PYTHONIOENCODING = 'utf-8'
    try {
        $out = @(& $Exe @Arguments 2>&1 | ForEach-Object { "$_" })
        $code = $LASTEXITCODE
    }
    finally {
        [Console]::OutputEncoding = $prevEnc
        $ErrorActionPreference = $prevEA
    }
    return [pscustomobject]@{ Exit = $code; Lines = $out }
}

function Get-EntryBytes([string]$Apk, [string]$Name) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
    try {
        $e = $zip.GetEntry($Name)
        if (-not $e) { return $null }
        $ms = New-Object System.IO.MemoryStream
        $s = $e.Open(); $s.CopyTo($ms); $s.Dispose()
        return $ms.ToArray()
    }
    finally { $zip.Dispose() }
}

function Get-Sha256([byte[]]$Data) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($Data)).Replace('-', '') }
    finally { $sha.Dispose() }
}

function Get-ZipEntryNames([string]$Apk) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
    try { return @($zip.Entries | ForEach-Object { $_.FullName }) } finally { $zip.Dispose() }
}

function Get-ZipStoredMap([string]$Apk) {
    $map = @{}
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
    try {
        foreach ($e in $zip.Entries) { $map[$e.FullName] = ($e.CompressedLength -eq $e.Length) }
    }
    finally { $zip.Dispose() }
    return $map
}

function Get-DexClassDescriptors([string]$Apk) {
    $dexBytes = Get-EntryBytes $Apk 'classes.dex'
    if (-not $dexBytes) { return @() }
    if (-not (Test-Path $DexDump)) { return @() }
    $tmp = Join-Path $env:TEMP ('xtc-verify-' + [guid]::NewGuid().ToString('N') + '.dex')
    [System.IO.File]::WriteAllBytes($tmp, $dexBytes)
    try {
        $dump = Invoke-Capture $DexDump @('-d', $tmp)
        return @($dump.Lines | ForEach-Object {
                if ($_ -match "Class descriptor\s+:\s+'(L[^']+;)'") { $matches[1] }
            })
    }
    finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
}

function Get-Xmltree([string]$Apk) {
    (Invoke-Capture $Aapt2 @('dump', 'xmltree', '--file', 'AndroidManifest.xml', $Apk)).Lines
}

function Get-ComponentClasses([string[]]$XmltreeLines) {
    $classes = New-Object System.Collections.Generic.List[string]
    $pending = $false
    foreach ($l in $XmltreeLines) {
        if ($l -match '^\s*E: (activity|activity-alias|service|receiver|provider)\b') { $pending = $true; continue }
        if ($pending) {
            if ($l -match 'android:name\(0x[0-9a-f]+\)="([^"]+)"') { $classes.Add($matches[1]); $pending = $false }
            elseif ($l -match '^\s*E: ') { $pending = $false }
        }
    }
    return @($classes | Sort-Object -Unique)
}

function Get-TrimmedVendorXmltree([string[]]$Lines) {
    # Remove the vendor lines that belong to one of the 6 cloud declarations
    # (element line + its whole subtree, judged by indentation). The strict
    # manifest must then be identical to the remainder.
    $remove = New-Object bool[] $Lines.Count
    $removedDecls = 0
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -notmatch '^\s*E: (service|receiver|provider|uses-permission)\b') { continue }
        $indent = $Lines[$i].Length - $Lines[$i].TrimStart().Length
        $name = $null
        for ($j = $i + 1; $j -lt $Lines.Count; $j++) {
            $jIndent = $Lines[$j].Length - $Lines[$j].TrimStart().Length
            if ($jIndent -le $indent) { break }
            if ($Lines[$j] -match 'android:name\(0x[0-9a-f]+\)="([^"]+)"') { $name = $matches[1]; break }
        }
        if (-not $name -or ($CloudTargets -notcontains $name)) { continue }
        $removedDecls++
        for ($k = $i; $k -lt $Lines.Count; $k++) {
            $kIndent = $Lines[$k].Length - $Lines[$k].TrimStart().Length
            if ($k -gt $i -and $kIndent -le $indent) { break }
            $remove[$k] = $true
        }
    }
    $kept = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $Lines.Count; $i++) { if (-not $remove[$i]) { $kept.Add($Lines[$i]) } }
    return [pscustomobject]@{ Lines = $kept.ToArray(); RemovedDeclarations = $removedDecls; RemovedLines = $Lines.Count - $kept.Count }
}

# ---------------------------------------------------------------- common groups
function Test-Packaging([string]$Apk, [string]$Tag) {
    Write-Line ''
    Write-Line "== [$Tag] 5/8 packaging (zipalign / compression / CRC / dex)"
    $align = Invoke-Capture $Zipalign @('-c', '-v', '4', $Apk)
    Report-Check 'zipalign -c -v 4 passes' ($align.Exit -eq 0) @($align.Lines | Select-Object -Last 2)

    $zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
    try {
        $arsc = $zip.GetEntry('resources.arsc')
        $arscStored = $arsc -and ($arsc.CompressedLength -eq $arsc.Length)
        $arscInfo = "compressed length: $($arsc.CompressedLength), length: $($arsc.Length)"
    }
    finally { $zip.Dispose() }
    Report-Check 'resources.arsc is STORED (required for targetSdk 30)' ([bool]$arscStored) @($arscInfo)

    $prodStored = Get-ZipStoredMap $Apk
    $vendorStored = Get-ZipStoredMap $OriginalApk
    $mismatch = @()
    foreach ($name in $vendorStored.Keys) {
        if ($prodStored.ContainsKey($name) -and $prodStored[$name] -ne $vendorStored[$name]) {
            $mismatch += "$name (vendor stored=$($vendorStored[$name]), product stored=$($prodStored[$name]))"
        }
    }
    Report-Check 'compression method preserved for every shared entry' ($mismatch.Count -eq 0) @("mismatches: $($mismatch.Count)")
    if ($mismatch.Count -gt 0) { $mismatch | Select-Object -First 10 | ForEach-Object { Write-Line "       $_" } }

    $crc = Invoke-Capture $PyExe @($ZipCheckScript, $Apk)
    Report-Check 'all zip entries read back with matching CRC-32' ($crc.Exit -eq 0) @(
        ($crc.Lines | Where-Object { $_ -match '^(entries|PASS|FAIL)' } | Select-Object -First 3))

    $dex = Get-EntryBytes $Apk 'classes.dex'
    if ($dex) {
        $tmp = Join-Path $env:TEMP ('xtc-verify-' + [guid]::NewGuid().ToString('N') + '.dex')
        [System.IO.File]::WriteAllBytes($tmp, $dex)
        $dd = Invoke-Capture $DexDump @('-f', $tmp)
        Report-Check 'classes.dex is a valid dex (dexdump)' ($dd.Exit -eq 0 -and $dd.Lines.Count -gt 0) @(
            ($dd.Lines | Where-Object { $_ -match 'DEX version' } | Select-Object -First 1))
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
        Write-Line ('   classes.dex   : {0:N0} bytes' -f $dex.Length)
    }
    else {
        Report-Check 'classes.dex present' $false @('classes.dex entry missing')
    }
}

function Test-Signature([string]$Apk, [string]$Tag) {
    Write-Line ''
    Write-Line "== [$Tag] 6/8 signature (apksigner verify --print-certs)"
    $verify = Invoke-Capture $JavaExe @('-jar', $ApkSignerJar, 'verify', '--verbose', '--print-certs', $Apk)
    $schemes = @($verify.Lines | Where-Object { $_ -match 'Verified using v[123] scheme' } | ForEach-Object { $_.Trim() })
    Report-Check 'apksigner verify passes (device minSdk path)' ($verify.Exit -eq 0) @($schemes)
    $cert = @($verify.Lines | Where-Object { $_ -match 'certificate DN' } | Select-Object -First 1)
    if ($cert.Count -gt 0) { Write-Line "       $($cert[0].Trim())" }

    $verifyV1 = Invoke-Capture $JavaExe @('-jar', $ApkSignerJar, 'verify', '--verbose', '--min-sdk-version', '21', '--print-certs', $Apk)
    $v1Line = @($verifyV1.Lines | Where-Object { $_ -match 'Verified using v1 scheme' } | ForEach-Object { $_.Trim() })
    $v1Ok = ($verifyV1.Exit -eq 0) -and (@($v1Line | Where-Object { $_ -match 'true' }).Count -gt 0)
    $warnCount = @($verifyV1.Lines | Where-Object { $_ -match 'not protected by signature' }).Count
    Report-Check 'v1 (JAR) signature verifies for pre-v2 installs (Android 8.1)' $v1Ok @(
        $v1Line,
        "apksigner META-INF warnings: $warnCount (vendor ships META-INF resources v1 cannot protect; expected)")
}

function Test-NoCloudGate([string]$Apk, [string]$Mode, [string]$Tag) {
    Write-Line ''
    Write-Line "== [$Tag] 8/8 no-cloud / no-network gate (mode=$Mode)"
    if ($SkipNoCloudScan) {
        Report-Warn 'no-cloud gate skipped (-SkipNoCloudScan)' @('this gate must stay enabled for any shipped build')
        return
    }
    foreach ($p in @($NoCloudGateScript, $CloudChecker)) {
        if (-not (Test-Path $p)) { Report-Check "no-cloud gate ($Mode)" $false @("missing: $p"); return }
    }
    if (-not $PyExe) { Report-Check "no-cloud gate ($Mode)" $false @('python launcher (py) not found'); return }

    $mdReport = Join-Path $BuildDir "no-cloud-$Mode.md"
    $rawScan = Invoke-Capture $PyExe @($CloudChecker, $Apk, '--report', $mdReport)
    $jsonPath = Join-Path $BuildDir "no-cloud-gate-$Mode.json"
    $gate = Invoke-Capture $PyExe @($NoCloudGateScript, $Apk, '--checker', $CloudChecker, '--mode', $Mode, '--json', $jsonPath)
    $gateOk = ($gate.Exit -eq 0)

    if ($Mode -eq 'strict') {
        Report-Check 'check_nocloud.py reports 0 residue (strict)' ($rawScan.Exit -eq 0) @(
            "checker exit code: $($rawScan.Exit) (0 = no banned class, no banned string, no java.net class)",
            "checker report: $mdReport")
    }
    else {
        Write-Line "   check_nocloud.py exit code: $($rawScan.Exit) (residue expected: the 4 audited no-op placeholders)"
        Write-Line "   checker report: $mdReport"
    }

    $details = @()
    if (Test-Path $jsonPath) {
        $data = Get-Content -Raw -Encoding UTF8 $jsonPath | ConvertFrom-Json
        $details += "classes.dex: $($data.dex_classes) classes / $($data.dex_bytes) bytes; rules: $($data.rules.banned_class_prefixes) class-prefix + $($data.rules.banned_strings) string"
        $details += "cloud classes hit: $(@($data.class_hits).Count) (allowed $(@($data.class_hits_allowed).Count), unexpected $(@($data.class_hits_unexpected).Count))"
        $details += "cloud strings hit: $(@($data.string_hits).Count) (allowed $(@($data.string_hits_allowed).Count), unexpected $(@($data.string_hits_unexpected).Count))"
        $details += "network classes: $(@($data.network_classes).Count)"
        if ($Mode -eq 'parity') {
            $details += 'whitelist (audited no-op placeholders that must stay for manifest parity):'
            foreach ($c in @($data.class_hits_allowed)) { $details += "  - class  $c  -> no-op placeholder, no network/telemetry" }
            foreach ($s in @($data.string_hits_allowed)) {
                $details += "  - string [$($s.patterns -join ',')] $($s.string)  -> $($s.reason)"
            }
        }
        foreach ($c in @($data.class_hits_unexpected)) { $details += "REJECTED class: $c" }
        foreach ($s in @($data.string_hits_unexpected)) { $details += "REJECTED string: $($s.string)" }
        foreach ($c in @($data.network_classes)) { $details += "REJECTED network class: $c" }
    }
    $title = if ($Mode -eq 'strict') { 'no-cloud gate: ZERO cloud/network residue (no whitelist)' }
    else { 'no-cloud gate: residue limited to the 4 audited no-op placeholder classes' }
    Report-Check $title $gateOk $details
    Write-Line "   gate json: $jsonPath"
}

# ------------------------------------------------------------- parity variant
function Invoke-ParityVariant([string]$Apk) {
    Write-Line ''
    Write-Line '########## PARITY VARIANT ##########'
    Write-Line "  apk    : $Apk"
    Write-Line "  vendor : $OriginalApk"
    $apkSize = (Get-Item $Apk).Length
    $dexBytes = Get-EntryBytes $Apk 'classes.dex'
    if (-not $dexBytes) { $dexBytes = New-Object byte[] 0 }
    $prodEntries = Get-ZipEntryNames $Apk
    $vendorEntries = Get-ZipEntryNames $OriginalApk
    Write-Line ('  size   : {0:N0} bytes ({1:N2} MB)' -f $apkSize, ($apkSize / 1MB))
    Write-Line ('  dex    : {0:N0} bytes' -f $dexBytes.Length)
    Write-Line "  entries: $($prodEntries.Count) (vendor $($vendorEntries.Count))"
    Write-Line ''
    Write-Line '== [parity] 0/8 size budget'
    Report-Check ('APK size within budget ({0:N0} <= {1:N0} bytes)' -f $apkSize, $MaxApkBytes) ($apkSize -le $MaxApkBytes)

    Write-Line ''
    Write-Line '== [parity] 1/8 badging (aapt2 dump badging)'
    $prodBadging = Invoke-Capture $Aapt2 @('dump', 'badging', $Apk)
    $origBadging = Invoke-Capture $Aapt2 @('dump', 'badging', $OriginalApk)
    $badgingDiff = @(Compare-Object $origBadging.Lines $prodBadging.Lines)
    Report-Check 'badging identical to vendor APK' ($badgingDiff.Count -eq 0) @(
        $(if ($badgingDiff.Count -eq 0) { "all $($prodBadging.Lines.Count) lines match (package/version/sdk/permissions/components/locales)" }
        else { $badgingDiff | Select-Object -First 20 | ForEach-Object { "({0}) {1}" -f $_.SideIndicator, $_.InputObject } }))

    Write-Line ''
    Write-Line '== [parity] 2/8 resources (aapt2 dump resources)'
    $prodRes = Invoke-Capture $Aapt2 @('dump', 'resources', $Apk)
    $origRes = Invoke-Capture $Aapt2 @('dump', 'resources', $OriginalApk)
    $resDiff = @(Compare-Object $origRes.Lines $prodRes.Lines)
    Report-Check 'resource table dump identical to vendor APK' ($resDiff.Count -eq 0) @(
        $(if ($resDiff.Count -eq 0) { "$($prodRes.Lines.Count) lines match" }
        else { $resDiff | Select-Object -First 20 | ForEach-Object { "({0}) {1}" -f $_.SideIndicator, $_.InputObject } }))

    Write-Line ''
    Write-Line '== [parity] 3/8 payload hashes'
    $arscProd = Get-EntryBytes $Apk 'resources.arsc'
    $arscOrig = Get-EntryBytes $OriginalApk 'resources.arsc'
    Report-Check 'resources.arsc byte-identical to vendor APK' ((Get-Sha256 $arscProd) -eq (Get-Sha256 $arscOrig)) @(
        "product sha256: $(Get-Sha256 $arscProd)", "vendor  sha256: $(Get-Sha256 $arscOrig)")
    $manProd = Get-EntryBytes $Apk 'AndroidManifest.xml'
    $manOrig = Get-EntryBytes $OriginalApk 'AndroidManifest.xml'
    Report-Check 'AndroidManifest.xml byte-identical to vendor APK' ((Get-Sha256 $manProd) -eq (Get-Sha256 $manOrig)) @(
        "product sha256: $(Get-Sha256 $manProd)", "vendor  sha256: $(Get-Sha256 $manOrig)")

    Write-Line ''
    Write-Line '== [parity] 4/8 zip entry set'
    $sigPattern = '^META-INF/(MANIFEST\.MF|.*\.(SF|RSA|DSA|EC))$'
    $onlyVendor = @($vendorEntries | Where-Object { $prodEntries -notcontains $_ })
    $onlyProduct = @($prodEntries | Where-Object { $vendorEntries -notcontains $_ })
    $unexpectedMissing = @($onlyVendor | Where-Object { $_ -notmatch $sigPattern })
    $unexpectedExtra = @($onlyProduct | Where-Object { $_ -ne 'classes.dex' -and $_ -notmatch $sigPattern })
    Report-Check 'entry set = vendor entries + classes.dex' (($unexpectedMissing.Count -eq 0) -and ($unexpectedExtra.Count -eq 0) -and ($prodEntries -contains 'classes.dex')) @(
        "vendor: $($vendorEntries.Count), product: $($prodEntries.Count)",
        "regenerated signature entries: $(@($onlyProduct | Where-Object { $_ -match $sigPattern }).Count)",
        "unexpected missing: $($unexpectedMissing.Count), unexpected extra: $($unexpectedExtra.Count)")

    Test-Packaging $Apk 'parity'
    Test-Signature $Apk 'parity'

    Write-Line ''
    Write-Line '== [parity] 7/8 manifest component classes'
    $components = Get-ComponentClasses (Get-Xmltree $Apk)
    $dexClasses = Get-DexClassDescriptors $Apk
    $missing = @($components | Where-Object { $dexClasses -notcontains ('L' + ($_ -replace '\.', '/') + ';') })
    Write-Line "   manifest components: $($components.Count); dex classes: $($dexClasses.Count)"
    Report-Check "all $($components.Count) manifest component classes present in the dex" ($missing.Count -eq 0) @(
        $(if ($missing.Count -eq 0) { 'all component classes compiled into classes.dex' }
        else { $missing | ForEach-Object { "MISSING CLASS: $_" } }))

    Test-NoCloudGate $Apk 'parity' 'parity'
}

# ------------------------------------------------------------- strict variant
function Invoke-StrictVariant([string]$Apk) {
    Write-Line ''
    Write-Line '########## STRICT (CLOUD-REMOVED) VARIANT ##########'
    Write-Line "  apk    : $Apk"
    Write-Line "  vendor : $OriginalApk"
    $apkSize = (Get-Item $Apk).Length
    $dexBytes = Get-EntryBytes $Apk 'classes.dex'
    if (-not $dexBytes) { $dexBytes = New-Object byte[] 0 }
    $prodEntries = Get-ZipEntryNames $Apk
    $vendorEntries = Get-ZipEntryNames $OriginalApk
    Write-Line ('  size   : {0:N0} bytes ({1:N2} MB)' -f $apkSize, ($apkSize / 1MB))
    Write-Line ('  dex    : {0:N0} bytes' -f $dexBytes.Length)
    Write-Line "  entries: $($prodEntries.Count) (vendor $($vendorEntries.Count))"
    Write-Line ''
    Write-Line '== [strict] 0/8 size budget'
    Report-Check ('APK size within budget ({0:N0} <= {1:N0} bytes)' -f $apkSize, $MaxApkBytes) ($apkSize -le $MaxApkBytes)

    Write-Line ''
    Write-Line '== [strict] 1/8 badging delta (only the removed cloud declarations)'
    $prodBadging = Invoke-Capture $Aapt2 @('dump', 'badging', $Apk)
    $origBadging = Invoke-Capture $Aapt2 @('dump', 'badging', $OriginalApk)
    $diff = @(Compare-Object $origBadging.Lines $prodBadging.Lines)
    $adds = @($diff | Where-Object { $_.SideIndicator -eq '=>' })
    $dels = @($diff | Where-Object { $_.SideIndicator -eq '<=' })
    $allowedDel = @($dels | Where-Object {
            $l = $_.InputObject
            ($l -match "uses-permission: name='com\.xtc\.http\.bigdata\.provider\.permission\.(READ|WRITE)_CONTENT'") -or
            ($l -match '^other-(activities|receivers|services)$') -or
            ($l -match '^provides-component:')
        })
    $unexpectedDel = @($dels | Where-Object { $allowedDel -notcontains $_ })
    $okDelta = ($adds.Count -eq 0) -and ($unexpectedDel.Count -eq 0)
    Report-Check 'badging delta = only the cloud permission/receiver lines' $okDelta @(
        "vendor lines: $($origBadging.Lines.Count), strict lines: $($prodBadging.Lines.Count)",
        "removed: $($dels.Count) (expected: 2 bigdata uses-permission + other-receivers)",
        "added: $($adds.Count) (must be 0)",
        $(if ($unexpectedDel.Count -gt 0) { $unexpectedDel | ForEach-Object { "UNEXPECTED REMOVAL: $($_.InputObject)" } } else { $null }))
    $dels | ForEach-Object { Write-Line "       removed: $($_.InputObject)" }

    Write-Line ''
    Write-Line '== [strict] 2/8 manifest surgery (aapt2 dump xmltree)'
    $vendorTree = Get-Xmltree $OriginalApk
    $strictTree = Get-Xmltree $Apk
    $expect = Get-TrimmedVendorXmltree $vendorTree
    $treeDiff = @(Compare-Object $expect.Lines $strictTree)
    Report-Check 'xmltree = vendor xmltree minus exactly the 6 cloud declarations' ($treeDiff.Count -eq 0) @(
        "vendor lines: $($vendorTree.Count), strict lines: $($strictTree.Count)",
        "removed declarations: $($expect.RemovedDeclarations) of 6 expected",
        "removed lines: $($expect.RemovedLines) (all inside those declarations)",
        "residual diff lines: $($treeDiff.Count)")
    if ($treeDiff.Count -gt 0) {
        $treeDiff | Select-Object -First 20 | ForEach-Object { Write-Line "       ({0}) {1}" -f $_.SideIndicator, $_.InputObject }
    }
    # belt and braces: none of the 6 targets may still be declared
    $strictComponents = Get-ComponentClasses $strictTree
    $stillDeclared = @($CloudTargets | Where-Object { $strictComponents -contains $_ })
    Report-Check 'all 6 cloud targets absent from the strict manifest declarations' ($stillDeclared.Count -eq 0) @(
        "still declared: $($stillDeclared.Count)")
    if ($stillDeclared.Count -gt 0) { $stillDeclared | ForEach-Object { Write-Line "       STILL DECLARED: $_" } }

    Write-Line ''
    Write-Line '== [strict] 3/8 payload hashes'
    $arscProd = Get-EntryBytes $Apk 'resources.arsc'
    $arscOrig = Get-EntryBytes $OriginalApk 'resources.arsc'
    Report-Check 'resources.arsc still byte-identical to vendor APK' ((Get-Sha256 $arscProd) -eq (Get-Sha256 $arscOrig)) @(
        "product sha256: $(Get-Sha256 $arscProd)", "vendor  sha256: $(Get-Sha256 $arscOrig)")
    $manProd = Get-EntryBytes $Apk 'AndroidManifest.xml'
    $manOrig = Get-EntryBytes $OriginalApk 'AndroidManifest.xml'
    $manChanged = (Get-Sha256 $manProd) -ne (Get-Sha256 $manOrig)
    Report-Check 'AndroidManifest.xml intentionally differs from vendor (stripped)' $manChanged @(
        "product sha256: $(Get-Sha256 $manProd)", "vendor  sha256: $(Get-Sha256 $manOrig)",
        ('product manifest: {0:N0} bytes, vendor: {1:N0} bytes' -f $manProd.Length, $manOrig.Length))

    Write-Line ''
    Write-Line '== [strict] 4/8 zip entry set'
    $sigPattern = '^META-INF/(MANIFEST\.MF|.*\.(SF|RSA|DSA|EC))$'
    $onlyVendor = @($vendorEntries | Where-Object { $prodEntries -notcontains $_ })
    $onlyProduct = @($prodEntries | Where-Object { $vendorEntries -notcontains $_ })
    $unexpectedMissing = @($onlyVendor | Where-Object { $_ -notmatch $sigPattern })
    $unexpectedExtra = @($onlyProduct | Where-Object { $_ -ne 'classes.dex' -and $_ -notmatch $sigPattern })
    Report-Check 'entry set = vendor entries + classes.dex' (($unexpectedMissing.Count -eq 0) -and ($unexpectedExtra.Count -eq 0) -and ($prodEntries -contains 'classes.dex')) @(
        "vendor: $($vendorEntries.Count), product: $($prodEntries.Count)",
        "regenerated signature entries: $(@($onlyProduct | Where-Object { $_ -match $sigPattern }).Count)",
        "unexpected missing: $($unexpectedMissing.Count), unexpected extra: $($unexpectedExtra.Count)")

    Test-Packaging $Apk 'strict'
    Test-Signature $Apk 'strict'

    Write-Line ''
    Write-Line '== [strict] 7/8 component classes (12 kept / 4 removed)'
    $dexClasses = Get-DexClassDescriptors $Apk
    $missing = @($strictComponents | Where-Object { $dexClasses -notcontains ('L' + ($_ -replace '\.', '/') + ';') })
    Report-Check "all $($strictComponents.Count) remaining component classes present in the dex" ($missing.Count -eq 0) @(
        $(if ($missing.Count -eq 0) { 'all remaining component classes compiled into classes.dex' }
        else { $missing | ForEach-Object { "MISSING CLASS: $_" } }))
    $stillCompiled = @($RemovedPlaceholderClasses | Where-Object { $dexClasses -contains $_ })
    Report-Check 'the 4 removed cloud placeholder classes are absent from the dex' ($stillCompiled.Count -eq 0) @(
        $(if ($stillCompiled.Count -eq 0) { "removed placeholders checked: $($RemovedPlaceholderClasses.Count)" }
        else { $stillCompiled | ForEach-Object { "STILL COMPILED: $_" } }))

    Test-NoCloudGate $Apk 'strict' 'strict'
}

# ------------------------------------------------------------------- run
Write-Host ''
Write-Host "verify: XTCSystemUI rebuild vs vendor APK  (variant=$Variant)" -ForegroundColor Cyan
foreach ($p in @($OriginalApk, $Aapt2, $Zipalign, $ApkSignerJar)) {
    if (-not (Test-Path $p)) { throw "missing required file: $p" }
}
$script:Report.Add("verify report -- $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
$script:Report.Add("  variant : $Variant")
$script:Report.Add("  vendor  : $OriginalApk")
if ($runParity) { $script:Report.Add("  parity  : $SignedApk") }
if ($runStrict) { $script:Report.Add("  strict  : $StrictApk") }

if ($runParity) { Invoke-ParityVariant $SignedApk }
if ($runStrict) { Invoke-StrictVariant $StrictApk }

Write-Line ''
Write-Line '== summary'
if ($script:Failures -eq 0) {
    $suffix = if ($script:Warnings -gt 0) { " ($($script:Warnings) warning(s) -- see above)" } else { '' }
    Write-Line "RESULT: PASS$suffix -- variant '$Variant' verified"
}
else {
    Write-Line "RESULT: FAIL -- $($script:Failures) check(s) failed, $($script:Warnings) warning(s)"
}
$null = New-Item -ItemType Directory -Force -Path $BuildDir
$script:Report | Set-Content -Encoding UTF8 $ReportPath
Write-Host ''
Write-Host "verify report: $ReportPath"
if ($script:Failures -ne 0) { exit 1 }
exit 0
