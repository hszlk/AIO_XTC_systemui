#requires -Version 5.1
<#
.SYNOPSIS
    One-command build for the hand-written XTCSystemUI 1.8.00 rebuild (Mode A).

.DESCRIPTION
    MODE A -- keep the vendor APK's resources byte-for-byte, replace only the code.

    The vendor APK ships with no classes.dex (the code was pre-optimised into the
    system vdex), but every other entry -- resources.arsc, res/**, assets/**,
    lib/**, the binary AndroidManifest.xml and the root files -- is intact and
    usable. Rebuilding those resources with aapt2 would (a) renumber every
    resource id, (b) fail outright on private framework references the vendor
    framework exposes but the public android.jar does not, and (c) lose the
    nine-patch borders that aapt2's crunch step stripped. So Mode A instead:

      1. javac  : compile app\src\main\java\**\*.java against android.jar only
      2. d8     : dex the classes (min-api 27)
      3. pack   : copy the original APK entry-by-entry (raw local headers and
                  compressed payloads, compression method preserved), drop the
                  stale v1 signature files, append the new classes.dex
      4. zipalign -p 4 -f
      5. apksigner (v1 + v2)

    Result: identical resource table, component set, locales, permissions and
    assets; only classes.dex differs from the vendor APK.

      build\XTCSystemUI-unsigned.apk   zipaligned, unsigned
      build\XTCSystemUI-signed.apk     zipaligned + apksigner v1/v2 signed

    Mode B (rebuild the resources from the jadx decode with aapt2) is kept as
    tools\build-aapt2.ps1.bak and documented in docs\BUILD.md as a fallback.

    VARIANTS
      parity (default) build\XTCSystemUI-signed.apk
                  information-parity build: vendor resources byte-identical,
                  16/16 manifest components including the four inert
                  cloud/telemetry placeholders.
      strict   build\XTCSystemUI-signed-strict.apk
                  cloud/telemetry-removed build: the binary manifest loses the
                  6 cloud declarations and the 4 placeholder classes are not
                  compiled. Implemented by analysis\cloud-control\tools\
                  build_strict_cloud.ps1 (owned by the Lead) -- this script only
                  orchestrates it, never duplicates it.
      both     produce both artifacts in one run.

.PARAMETER Clean
    Delete build\ (all intermediates and artifacts) before building.

.PARAMETER SkipVerify
    Do not run tools\verify.ps1 at the end.

.PARAMETER SkipJava
    Reuse the previously built build\obj\classes.dex instead of re-running
    javac+d8 (useful while only the packaging/verification steps change).

.PARAMETER Variant
    parity (default) | strict | both. -StrictCloud is a shorthand for
    "-Variant both".

.PARAMETER StrictCloud
    Build the strict cloud-removal variant as well (same as -Variant both).

.PARAMETER SkipNoCloudScan
    Skip the no-cloud/no-network gate in verify.ps1 (not recommended: it is the
    gate that proves the strict variant carries no cloud residue).

.EXAMPLE
    pwsh -File tools\build.ps1
    pwsh -File tools\build.ps1 -Clean
#>
[CmdletBinding()]
param(
    [switch]$Clean,
    [switch]$SkipVerify,
    [switch]$SkipJava,
    [ValidateSet('both', 'parity', 'strict')][string]$Variant = 'parity',
    [switch]$StrictCloud,
    [switch]$SkipNoCloudScan
)

# -StrictCloud is shorthand for -Variant both; an explicit -Variant always wins.
if ($StrictCloud -and $Variant -eq 'parity') { $Variant = 'both' }
$BuildParity = ($Variant -eq 'parity' -or $Variant -eq 'both')
$BuildStrict = ($Variant -eq 'strict' -or $Variant -eq 'both')

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

# --------------------------------------------------------------------- paths
$RebuildRoot = Split-Path -Parent $PSScriptRoot
$SrcDir = Join-Path $RebuildRoot 'app\src\main'
# Java sources: the Wave-1 runtime written by the Lead lives outside this project
# and is read-only here; app\src\main\java only holds bootstrap fallbacks that are
# used when a relative path is missing from the primary tree.
$PrimaryJavaDir = 'D:\DEBUG\apk\sysui\src\main\java'
$LocalJavaDir = Join-Path $SrcDir 'java'
$ManifestCopy = Join-Path $SrcDir 'AndroidManifest.xml'
$BuildDir = Join-Path $RebuildRoot 'build'
$ObjDir = Join-Path $BuildDir 'obj'
$ClassDir = Join-Path $ObjDir 'classes'
$DexDir = Join-Path $ObjDir 'dex'
$Skeleton = Join-Path $ObjDir 'skeleton.apk'
$KeyStore = Join-Path $RebuildRoot 'keystore\debug.keystore'
$Unsigned = Join-Path $BuildDir 'XTCSystemUI-unsigned.apk'
$Signed = Join-Path $BuildDir 'XTCSystemUI-signed.apk'
$PackScript = Join-Path $PSScriptRoot 'apk-pack.ps1'
$VerifyScript = Join-Path $PSScriptRoot 'verify.ps1'
$HiddenApiScript = Join-Path $PSScriptRoot 'check-hidden-api.ps1'
$ZipCheckScript = Join-Path $PSScriptRoot 'zip_check.py'
$NoCloudGateScript = Join-Path $PSScriptRoot 'nocloud_gate.py'

# strict cloud-removal variant (implemented by the Lead, orchestrated here)
$CloudRoot = 'D:\DEBUG\apk\sysui\analysis\cloud-control'
$CloudBuildScript = Join-Path $CloudRoot 'tools\build_strict_cloud.ps1'
$CloudStrictApk = Join-Path $CloudRoot 'out\XTCSystemUI-strict-cloud.apk'
$StrictSigned = Join-Path $BuildDir 'XTCSystemUI-signed-strict.apk'
$StrictReportPath = Join-Path $BuildDir 'build-report-strict.txt'
$PyExe = (Get-Command py -ErrorAction SilentlyContinue).Source
if (-not $PyExe) { $PyExe = (Get-Command python -ErrorAction SilentlyContinue).Source }

# vendor APK = resource skeleton + provenance (read-only)
$OriginalApk = 'D:\DEBUG\apk\sysui\XTCSystemUI.apk'

# toolchain (offline SDK, no Gradle, no network)
$SdkRoot = 'C:\Users\10413\AppData\Local\Android\Sdk'
$BuildTools = Join-Path $SdkRoot 'build-tools\30.0.3'
$AndroidJar = Join-Path $SdkRoot 'platforms\android-30\android.jar'
$Zipalign = Join-Path $BuildTools 'zipalign.exe'
# The build-tools *.bat wrappers are unusable in this SDK: they look for
# build-tools\..\tools\lib\find_java.bat, which no longer ships, so d8.bat
# hits `if not defined java_exe goto :EOF` and exits 0 without doing anything.
# Call the jars through java.exe directly instead.
$JavaExe = (Get-Command java -ErrorAction Stop).Source
# d8/R8 from build-tools 30.0.3 and 34.0.0 crash with an internal
# NullPointerException on an anonymous inner class declared in a field
# initialiser (com\android\systemui\SystemUIApplication$1). R8 8.x from
# build-tools 36.0.0 handles it correctly; the other tools stay on 30.0.3 so the
# packaging/verification stack matches the target platform.
$DexBuildTools = Join-Path $SdkRoot 'build-tools\36.0.0'
$D8Jar = Join-Path $DexBuildTools 'lib\d8.jar'
$ApkSignerJar = Join-Path $BuildTools 'lib\apksigner.jar'

$PackageName = 'com.android.systemui'
$MinSdk = '27'
$MaxApkBytes = 3MB

$script:Timings = New-Object System.Collections.Generic.List[object]
$script:StopwatchAll = [Diagnostics.Stopwatch]::StartNew()

# ------------------------------------------------------------------ helpers
function Format-Bytes([long]$b) {
    if ($b -ge 1MB) { return ('{0:N2} MB' -f ($b / 1MB)) }
    if ($b -ge 1KB) { return ('{0:N1} KB' -f ($b / 1KB)) }
    return "$b B"
}

function Write-Section([string]$text) {
    Write-Host ''
    Write-Host "== $text" -ForegroundColor Cyan
}

function Invoke-Tool {
    param(
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][string]$Exe,
        [string[]]$Arguments = @(),
        [string]$WorkDir
    )
    if (-not (Test-Path $Exe)) { throw "$($Title): tool not found: ${Exe}" }
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $exit = 0
    $output = @()
    try {
        if ($WorkDir) { Push-Location $WorkDir }
        $output = @(& $Exe @Arguments 2>&1 | ForEach-Object { "$_" })
        $exit = $LASTEXITCODE
    }
    finally {
        if ($WorkDir) { Pop-Location }
        $ErrorActionPreference = $prev
    }
    $sw.Stop()
    if ($exit -ne 0) {
        $tail = ($output | Select-Object -Last 25) -join [Environment]::NewLine
        throw "$Title failed (exit $exit)`n$tail"
    }
    $script:Timings.Add([pscustomobject]@{ Step = $Title; Seconds = [math]::Round($sw.Elapsed.TotalSeconds, 2) })
    Write-Host ('   {0,-32} {1,7:N2}s' -f $Title, $sw.Elapsed.TotalSeconds)
    return , $output
}

function Assert-Path([string]$Path, [string]$What) {
    if (-not (Test-Path $Path)) { throw "expected $What at $Path (build step produced nothing)" }
}

# Create the shared debug keystore on first use. Both the parity build and the
# strict variant (analysis\cloud-control\tools\build_strict_cloud.ps1) sign with
# rebuild\keystore\debug.keystore, so it has to exist before either runs.
function Ensure-DebugKeyStore {
    if (Test-Path $KeyStore) {
        Write-Host "   reusing $KeyStore"
        return
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $KeyStore) | Out-Null
    Invoke-Tool -Title 'keytool (debug keystore)' -Exe (Get-Command keytool).Source -Arguments @(
        '-genkeypair', '-v'
        '-keystore', $KeyStore
        '-storetype', 'PKCS12'
        '-storepass', 'android'
        '-keypass', 'android'
        '-alias', 'androiddebugkey'
        '-keyalg', 'RSA'
        '-keysize', '2048'
        '-validity', '10000'
        '-dname', 'CN=Android Debug,O=Android,C=US'
    ) | Out-Null
    Write-Host "   generated $KeyStore"
}

<#
    Run the Lead-owned strict cloud-removal build and publish its artifact as
    build\XTCSystemUI-signed-strict.apk.

    The six steps (AXML strip -> source exclusion -> javac -> d8 -> repack ->
    zipalign -> apksigner) live in analysis\cloud-control\tools\
    build_strict_cloud.ps1 and are NOT duplicated here; this function only
    orchestrates, publishes the artifact and records provenance. The Lead's
    script writes its own intermediates under analysis\cloud-control\{build,out},
    which is why this project never writes there directly.
#>
function Invoke-StrictCloudBuild {
    param([switch]$CleanCloud, [string]$StepLabel = '8/9 strict cloud-removal variant')
    Write-Section $StepLabel
    if (-not (Test-Path $CloudBuildScript)) {
        throw "strict cloud build script not found: $CloudBuildScript (owned by the Lead)"
    }
    Ensure-DebugKeyStore
    if (-not $PyExe) { throw 'python launcher (py) not found; the strict variant needs it' }

    $cloudArgs = @()
    if ($CleanCloud) { $cloudArgs += '-Clean' }
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $cloudOut = @()
    $code = 0
    # The Lead's script shells out to python, which prints UTF-8 (Chinese report
    # text). Match the console encoding so the build log stays readable.
    $prevEnc = [Console]::OutputEncoding
    $prevPyEnc = $env:PYTHONIOENCODING
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $env:PYTHONIOENCODING = 'utf-8'
    try {
        $cloudOut = @(& $CloudBuildScript @cloudArgs 2>&1 | ForEach-Object { "$_" })
        $code = $LASTEXITCODE
    }
    catch {
        $code = 1
        $cloudOut += "$_"
    }
    finally {
        [Console]::OutputEncoding = $prevEnc
        $env:PYTHONIOENCODING = $prevPyEnc
    }
    $sw.Stop()
    $script:Timings.Add([pscustomobject]@{ Step = 'strict cloud variant'; Seconds = [math]::Round($sw.Elapsed.TotalSeconds, 2) })
    Write-Host ('   {0,-32} {1,7:N2}s' -f 'strict cloud variant', $sw.Elapsed.TotalSeconds)
    $cloudOut | ForEach-Object { Write-Host "   $_" }
    if ($code -ne 0) {
        throw ("strict cloud build failed (exit {0})`n{1}" -f $code, (($cloudOut | Select-Object -Last 25) -join [Environment]::NewLine))
    }
    Assert-Path $CloudStrictApk 'strict cloud APK'
    Copy-Item $CloudStrictApk $StrictSigned -Force
    Assert-Path $StrictSigned 'published strict APK'
    if (Test-Path (Join-Path $BuildDir 'XTCSystemUI-signed-strict.apk.idsig')) {
        Remove-Item (Join-Path $BuildDir 'XTCSystemUI-signed-strict.apk.idsig') -Force
    }
    $strictSize = (Get-Item $StrictSigned).Length
    if ($strictSize -gt $MaxApkBytes) {
        throw ('strict APK exceeds the {0} budget: {1} bytes' -f (Format-Bytes $MaxApkBytes), $strictSize)
    }
    Write-Host "   strict apk: $(Format-Bytes $strictSize) -> $StrictSigned"
}

# Run verify.ps1 for one variant and remember a non-zero exit code without
# aborting the other variant's verification.
function Invoke-VariantVerify {
    param([hashtable]$Arguments, [string]$Label)
    & $VerifyScript @Arguments
    if ($LASTEXITCODE -ne 0) {
        Write-Host "$Label VERIFY FAILED" -ForegroundColor Red
        return $LASTEXITCODE
    }
    return 0
}

Write-Host 'XTCSystemUI 1.8.00 rebuild -- Mode A (vendor resources + hand-written code)' -ForegroundColor Green
Write-Host ("  variant     : {0}      (parity={1} strict={2})" -f $Variant, $BuildParity, $BuildStrict)
Write-Host "  project     : $RebuildRoot"
Write-Host "  skeleton apk: $OriginalApk"
Write-Host "  sdk         : $SdkRoot (build-tools 30.0.3, platform android-30)"

# ---------------------------------------- strict-only path (skips parity build)
if (-not $BuildParity) {
    Write-Section 'strict-only build (-Variant strict)'
    if (-not (Test-Path $CloudBuildScript)) { throw "strict cloud build script not found: $CloudBuildScript (owned by the Lead)" }
    if (-not $PyExe) { throw 'python launcher (py) not found; the strict variant needs it' }
    if ($Clean -and (Test-Path $BuildDir)) {
        Remove-Item $BuildDir -Recurse -Force
        Write-Host '   removed build\'
    }
    if (-not (Test-Path $BuildDir)) { New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null }
    if (Test-Path $StrictSigned) { Remove-Item $StrictSigned -Force }
    Invoke-StrictCloudBuild -CleanCloud:$Clean -StepLabel '1/3 strict cloud-removal variant'
    $strictSize = (Get-Item $StrictSigned).Length
    $strictSha = (Get-FileHash $StrictSigned -Algorithm SHA256).Hash
    Write-Section '2/3 strict build report'
    $report = New-Object System.Collections.Generic.List[string]
    $report.Add('XTCSystemUI 1.8.00 rebuild (Mode A, strict cloud-removal) -- build report')
    $report.Add("generated       : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
    $report.Add('variant         : strict (6 cloud declarations + 4 placeholder classes removed)')
    $report.Add("strict builder  : $CloudBuildScript")
    $report.Add("signed apk      : $StrictSigned")
    $report.Add(('signed apk size : {0} ({1} bytes)' -f (Format-Bytes $strictSize), $strictSize))
    $report.Add("sha256          : $strictSha")
    $report | Set-Content -Encoding UTF8 $StrictReportPath
    $report | ForEach-Object { Write-Host "   $_" }
    Write-Host ''
    Write-Host "BUILD OK  ->  $StrictSigned  ($(Format-Bytes $strictSize))" -ForegroundColor Green
    if (-not $SkipVerify) {
        Write-Section '3/3 verify (strict)'
        $vArgs = @{ StrictApk = $StrictSigned; OriginalApk = $OriginalApk; Variant = 'strict' }
        if ($SkipNoCloudScan) { $vArgs['SkipNoCloudScan'] = $true }
        & $VerifyScript @vArgs
        if ($LASTEXITCODE -ne 0) { Write-Host 'VERIFY FAILED' -ForegroundColor Red; exit $LASTEXITCODE }
    }
    exit 0
}

# ---------------------------------------------------------- 0. preflight
Write-Section '0/9 preflight'
foreach ($p in @($OriginalApk, $ManifestCopy, $PackScript, $VerifyScript, $HiddenApiScript)) {
    if (-not (Test-Path $p)) { throw "missing required input: $p" }
}
if (-not (Test-Path $PrimaryJavaDir) -and -not (Test-Path $LocalJavaDir)) {
    throw "no Java source tree found (looked for $PrimaryJavaDir and $LocalJavaDir)"
}
foreach ($p in @($Zipalign, $D8Jar, $ApkSignerJar, $AndroidJar)) {
    if (-not (Test-Path $p)) { throw "missing toolchain component: $p" }
}
foreach ($exe in @('java', 'javac', 'keytool')) {
    if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { throw "$exe not found on PATH" }
}
Write-Host '   inputs and tools present'

# ------------------------------------- 1. clean + check manifest provenance
Write-Section '1/9 clean + manifest check'
if ($Clean -and (Test-Path $BuildDir)) {
    Remove-Item $BuildDir -Recurse -Force
    Write-Host '   removed build\'
}
foreach ($d in @($ObjDir, $ClassDir, $DexDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
}
foreach ($f in @($Unsigned, $Signed, $Skeleton, $StrictSigned)) { if (Test-Path $f) { Remove-Item $f -Force } }
Get-ChildItem $BuildDir -File -Filter '*.idsig' -ErrorAction SilentlyContinue | Remove-Item -Force

# The manifest inside the delivered APK comes from the vendor APK; keep the
# project copy as an auditable, byte-identical reference and fail if it drifted.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($OriginalApk)
try {
    $entry = $z.GetEntry('AndroidManifest.xml')
    if (-not $entry) { throw 'vendor APK has no AndroidManifest.xml entry' }
    $ms = New-Object System.IO.MemoryStream
    $st = $entry.Open(); $st.CopyTo($ms); $st.Dispose()
    $vendorManifest = $ms.ToArray(); $ms.Dispose()
}
finally { $z.Dispose() }
$projectManifest = [System.IO.File]::ReadAllBytes($ManifestCopy)
if ($vendorManifest.Length -ne $projectManifest.Length) {
    throw "app\src\main\AndroidManifest.xml is not the vendor binary manifest ($($projectManifest.Length) vs $($vendorManifest.Length) bytes); re-extract it from the vendor APK"
}
for ($i = 0; $i -lt $vendorManifest.Length; $i++) {
    if ($vendorManifest[$i] -ne $projectManifest[$i]) {
        throw "app\src\main\AndroidManifest.xml differs from the vendor manifest at byte $i; re-extract it from the vendor APK"
    }
}
Write-Host "   manifest: vendor binary manifest verified ($($vendorManifest.Length) bytes, byte-identical)"

# ------------------------------------------------------------- 2. javac
Write-Section '2/9 javac'
if ($SkipJava) {
    Write-Host '   skipped (-SkipJava)'
    $dexFile = Join-Path $DexDir 'classes.dex'
    Assert-Path $dexFile 'existing classes.dex (-SkipJava)'
}
else {
    Remove-Item $ClassDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $ClassDir | Out-Null
    # Merge the source trees by path relative to each root; the primary (Lead)
    # tree wins so the bootstrap skeletons never shadow the real implementation.
    $sourceMap = @{}
    $usedDirs = New-Object System.Collections.Generic.List[string]
    foreach ($dir in @($PrimaryJavaDir, $LocalJavaDir)) {
        if (-not (Test-Path $dir)) { continue }
        $usedDirs.Add($dir)
        foreach ($f in Get-ChildItem $dir -Recurse -File -Filter '*.java') {
            $rel = $f.FullName.Substring($dir.Length).TrimStart('\', '/')
            if (-not $sourceMap.ContainsKey($rel)) { $sourceMap[$rel] = $f.FullName }
        }
    }
    $javaSources = @($sourceMap.Values | Sort-Object)
    if ($javaSources.Count -eq 0) { throw 'no Java sources found' }
    $hasR = @($javaSources | Where-Object { $_ -like '*\R.java' }).Count -gt 0
    Write-Host "   source roots: $($usedDirs -join ' ; ')"
    Write-Host "   sources: $($javaSources.Count) (R.java present: $hasR)"
    Invoke-Tool -Title 'javac' -Exe (Get-Command javac).Source -Arguments (@(
            '--release', '8', '-encoding', 'UTF-8', '-nowarn'
            '-classpath', $AndroidJar
            '-d', $ClassDir
        ) + $javaSources) | Out-Null
    $classFiles = @(Get-ChildItem $ClassDir -Recurse -File -Filter '*.class')
    if ($classFiles.Count -eq 0) { throw 'javac produced no .class files' }
    Write-Host "   $($classFiles.Count) class file(s)"

    # ------------------------------------------------------------- 3. d8
    Write-Section '3/9 d8'
    Remove-Item $DexDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $DexDir | Out-Null
    Invoke-Tool -Title 'd8' -Exe $JavaExe -Arguments (@(
            '-cp', $D8Jar, 'com.android.tools.r8.D8'
            '--min-api', $MinSdk, '--release'
            '--lib', $AndroidJar
            '--output', $DexDir
        ) + ($classFiles | ForEach-Object { $_.FullName })) | Out-Null
    $dexFile = Join-Path $DexDir 'classes.dex'
    Assert-Path $dexFile 'classes.dex'
    Write-Host "   classes.dex: $(Format-Bytes (Get-Item $dexFile).Length)"
}

# --------------------------------------- 3b. hidden-API gate (framework-only)
Write-Section '3b/9 hidden-API static check'
$hiddenDirs = @()
if (Test-Path $PrimaryJavaDir) { $hiddenDirs += $PrimaryJavaDir }
if (Test-Path $LocalJavaDir) { $hiddenDirs += $LocalJavaDir }
$hiddenOut = & $HiddenApiScript -SourceDir $hiddenDirs -DexFile $dexFile 2>&1
$hiddenCode = $LASTEXITCODE
$hiddenOut | ForEach-Object { Write-Host "   $_" }
$hiddenSummary = ($hiddenOut | Where-Object { $_ -match 'RESULT' } | Select-Object -First 1)
Set-Content -Encoding UTF8 (Join-Path $BuildDir 'hidden-api-check.txt') @($hiddenOut)
if ($hiddenCode -ne 0) { throw 'hidden-API check failed (see build\hidden-api-check.txt)' }

# --------------------------------------------- 4. pack skeleton + dex
Write-Section '4/9 pack vendor resources + classes.dex'
$swPack = [Diagnostics.Stopwatch]::StartNew()
& $PackScript -SourceApk $OriginalApk -DexFile $dexFile -OutputApk $Skeleton | ForEach-Object { Write-Host "   $_" }
$packExit = $LASTEXITCODE
$swPack.Stop()
if ($packExit -ne 0) { throw 'apk-pack failed' }
Assert-Path $Skeleton 'packed skeleton APK'
$script:Timings.Add([pscustomobject]@{ Step = 'pack'; Seconds = [math]::Round($swPack.Elapsed.TotalSeconds, 2) })
Write-Host ('   {0,-32} {1,7:N2}s' -f 'pack', $swPack.Elapsed.TotalSeconds)
Write-Host "   skeleton: $(Format-Bytes (Get-Item $Skeleton).Length)"

# ------------------------------------------------------- 5. zipalign
Write-Section '5/9 zipalign'
Invoke-Tool -Title 'zipalign' -Exe $Zipalign -Arguments @('-p', '-f', '4', $Skeleton, $Unsigned) | Out-Null
Assert-Path $Unsigned 'zipaligned APK'
Write-Host "   unsigned: $(Format-Bytes (Get-Item $Unsigned).Length)"

# ------------------------------------------------------- 6. sign
Write-Section '6/9 apksigner'
Ensure-DebugKeyStore
Invoke-Tool -Title 'apksigner' -Exe $JavaExe -Arguments @(
    '-jar', $ApkSignerJar
    'sign'
    '--ks', $KeyStore
    '--ks-pass', 'pass:android'
    '--key-pass', 'pass:android'
    '--v1-signing-enabled', 'true'
    '--v2-signing-enabled', 'true'
    '--v3-signing-enabled', 'true'
    '--v4-signing-enabled', 'false'
    '--out', $Signed
    $Unsigned
) | Out-Null
Assert-Path $Signed 'signed APK'

# ------------------------------------------------------- 7. summary
Write-Section '7/9 summary'
$apkSize = (Get-Item $Signed).Length
$dexSize = (Get-Item $dexFile).Length
$z = [System.IO.Compression.ZipFile]::OpenRead($Signed)
try { $entryCount = $z.Entries.Count } finally { $z.Dispose() }
$script:StopwatchAll.Stop()

$report = New-Object System.Collections.Generic.List[string]
$report.Add('XTCSystemUI 1.8.00 rebuild (Mode A) -- build report')
$report.Add("generated       : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
$report.Add("package         : $PackageName")
$report.Add("mode            : A (vendor resources kept byte-identical)")
$report.Add("skeleton        : $OriginalApk")
$report.Add("signed apk      : $Signed")
$report.Add(('signed apk size : {0} ({1} bytes)' -f (Format-Bytes $apkSize), $apkSize))
$report.Add(('classes.dex     : {0} ({1} bytes)' -f (Format-Bytes $dexSize), $dexSize))
$report.Add("apk entries     : $entryCount")
$report.Add("size budget     : $(Format-Bytes $MaxApkBytes) -- $(if ($apkSize -le $MaxApkBytes) { 'OK' } else { 'EXCEEDED' })")
$report.Add('')
$report.Add('step timings:')
foreach ($t in $script:Timings) { $report.Add(('  {0,-32} {1,8:N2}s' -f $t.Step, $t.Seconds)) }
$report.Add(('  {0,-32} {1,8:N2}s' -f 'TOTAL', $script:StopwatchAll.Elapsed.TotalSeconds))

$report | Set-Content -Encoding UTF8 (Join-Path $BuildDir 'build-report.txt')
$report | ForEach-Object { Write-Host "   $_" }

if ($apkSize -gt $MaxApkBytes) {
    Write-Host "BUILD FAILED: signed APK exceeds the $(Format-Bytes $MaxApkBytes) budget" -ForegroundColor Red
    exit 1
}
Write-Host ''
Write-Host "BUILD OK  ->  $Signed  ($(Format-Bytes $apkSize))" -ForegroundColor Green

# ------------------------------------- 8/9 strict cloud-removal variant
if ($BuildStrict) {
    Invoke-StrictCloudBuild -CleanCloud:$Clean
    $strictSize = (Get-Item $StrictSigned).Length
    $strictSha = (Get-FileHash $StrictSigned -Algorithm SHA256).Hash
    $zStrict = [System.IO.Compression.ZipFile]::OpenRead($StrictSigned)
    try {
        $strictEntries = $zStrict.Entries.Count
        $strictDexEntry = $zStrict.GetEntry('classes.dex')
        $strictDexSize = if ($strictDexEntry) { $strictDexEntry.Length } else { 0 }
    }
    finally { $zStrict.Dispose() }

    Write-Section '8/9 strict build report'
    $strictReport = New-Object System.Collections.Generic.List[string]
    $strictReport.Add('XTCSystemUI 1.8.00 rebuild (Mode A) -- STRICT cloud-removal build report')
    $strictReport.Add("generated       : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
    $strictReport.Add('variant         : strict (6 cloud declarations + 4 placeholder classes removed)')
    $strictReport.Add("strict builder  : $CloudBuildScript  (Lead-owned, orchestrated by build.ps1)")
    $strictReport.Add("signed apk      : $StrictSigned")
    $strictReport.Add("source artifact : $CloudStrictApk")
    $strictReport.Add(('signed apk size : {0} ({1} bytes)' -f (Format-Bytes $strictSize), $strictSize))
    $strictReport.Add("sha256          : $strictSha")
    $strictReport.Add(('classes.dex     : {0:N0} bytes' -f $strictDexSize))
    $strictReport.Add("apk entries     : $strictEntries")
    $strictReport.Add("size budget     : $(Format-Bytes $MaxApkBytes) -- $(if ($strictSize -le $MaxApkBytes) { 'OK' } else { 'EXCEEDED' })")
    $strictReport.Add('')
    $strictReport.Add('step timings:')
    foreach ($t in $script:Timings) { $strictReport.Add(('  {0,-32} {1,8:N2}s' -f $t.Step, $t.Seconds)) }
    $strictReport | Set-Content -Encoding UTF8 $StrictReportPath
    $strictReport | ForEach-Object { Write-Host "   $_" }
    Write-Host ''
    Write-Host "BUILD OK  ->  $StrictSigned  ($(Format-Bytes $strictSize))" -ForegroundColor Green
}

# ------------------------------------------------------- 9/9 verify
if (-not $SkipVerify) {
    Write-Section '9/9 verify'
    $verifyExit = 0
    if ($BuildParity) {
        $vArgs = @{ SignedApk = $Signed; OriginalApk = $OriginalApk; Variant = 'parity' }
        if ($SkipNoCloudScan) { $vArgs['SkipNoCloudScan'] = $true }
        & $VerifyScript @vArgs
        if ($LASTEXITCODE -ne 0) {
            $verifyExit = $LASTEXITCODE
            Write-Host 'PARITY VERIFY FAILED' -ForegroundColor Red
        }
    }
    if ($BuildStrict) {
        $vArgs = @{ StrictApk = $StrictSigned; OriginalApk = $OriginalApk; Variant = 'strict' }
        if ($SkipNoCloudScan) { $vArgs['SkipNoCloudScan'] = $true }
        & $VerifyScript @vArgs
        if ($LASTEXITCODE -ne 0) {
            $verifyExit = $LASTEXITCODE
            Write-Host 'STRICT VERIFY FAILED' -ForegroundColor Red
        }
    }
    if ($verifyExit -ne 0) { exit $verifyExit }
}
