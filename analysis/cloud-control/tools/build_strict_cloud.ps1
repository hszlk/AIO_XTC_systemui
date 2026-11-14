#requires -Version 5.1
<#
.SYNOPSIS
    Build the STRICT cloud-control-removal APK variant of XTCSystemUI 1.8.00.

.DESCRIPTION
    Same Mode A packaging as rebuild\tools\build.ps1 (vendor resources byte
    identical, only classes.dex replaced) with two additional removal steps:

      1. the binary AndroidManifest.xml is stripped of the cloud/telemetry
         component declarations (see tools\cloud-targets.json) using the AXML
         surgery script; string pool / resource map stay untouched;
      2. the Java sources of those no-op placeholders are excluded from the
         dex, so no cloud-package class survives in the APK.

    Outputs (analysis\cloud-control\out\):
      XTCSystemUI-strict-cloud.apk   signed strict variant
      manifest-strict.xml           stripped binary manifest (evidence)
      NO-CLOUD-REPORT.md            static residue scan result

.PARAMETER Clean
    Remove the out\ and build\ intermediates first.

.PARAMETER SkipSign
    Stop after the unsigned APK (useful for inspection).
#>
param(
    [switch]$Clean,
    [switch]$SkipSign
)

$script:ErrorActionPreference = 'Continue'
$Root = 'D:\DEBUG\apk\sysui'
$CloudRoot = Join-Path $Root 'analysis\cloud-control'
$Tools = Join-Path $CloudRoot 'tools'
$OutDir = Join-Path $CloudRoot 'out'
$BuildDir = Join-Path $CloudRoot 'build'
$JavaSrc = Join-Path $Root 'src\main\java'
$VendorApk = Join-Path $Root 'XTCSystemUI.apk'

$SdkRoot = 'C:\Users\10413\AppData\Local\Android\Sdk'
$BuildTools = Join-Path $SdkRoot 'build-tools\30.0.3'
$DexBuildTools = Join-Path $SdkRoot 'build-tools\36.0.0'
$AndroidJar = Join-Path $SdkRoot 'platforms\android-30\android.jar'
$Zipalign = Join-Path $BuildTools 'zipalign.exe'
$D8Jar = Join-Path $DexBuildTools 'lib\d8.jar'
$ApkSignerJar = Join-Path $BuildTools 'lib\apksigner.jar'
$KeyStore = Join-Path $Root 'rebuild\keystore\debug.keystore'
$JavaExe = (Get-Command java).Source
$JavacExe = (Get-Command javac).Source
$PyExe = (Get-Command py).Source

$ExcludedSources = @(
    'com\xtc\xtcoco\CodeCoverageReceiver.java',
    'com\xtc\bigdata\monitor\receiver\MonitorStartReceiver.java',
    'com\xtc\dns\provider\HttpDnsContentProvider.java',
    'com\xtc\bigdata\collector\exception\CrashIntentService.java'
)

function Step([string]$text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }
function Assert-Path([string]$path, [string]$what) {
    if (-not (Test-Path $path)) { throw "missing $what : $path" }
}
function Invoke-Native([string]$exe, [string[]]$arguments, [string]$title) {
    $errFile = Join-Path $BuildDir 'stderr.txt'
    Remove-Item $errFile -ErrorAction SilentlyContinue
    & $exe @arguments 2> $errFile
    $code = $LASTEXITCODE
    if ((Test-Path $errFile) -and (Get-Item $errFile).Length -gt 0) {
        $text = (Get-Content $errFile -Raw).Trim()
        if ($text) { Write-Host $text }
    }
    if ($code -ne 0) { throw "$title failed (exit $code)" }
}

if ($Clean) {
    Remove-Item $OutDir -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $BuildDir -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $OutDir, $BuildDir | Out-Null

# ---------------------------------------------------------------- 1. manifest
Step '1/6 strip cloud declarations from the binary manifest'
$vendorManifest = Join-Path $OutDir 'manifest-vendor.xml'
& $PyExe -c "import zipfile;open(r'$vendorManifest','wb').write(zipfile.ZipFile(r'$VendorApk').read('AndroidManifest.xml'))"
if ($LASTEXITCODE -ne 0) { throw 'manifest extraction failed' }
$strictManifest = Join-Path $OutDir 'manifest-strict.xml'
Invoke-Native $PyExe @((Join-Path $Tools 'axml_strip.py'), $vendorManifest, $strictManifest, '--targets', (Join-Path $Tools 'cloud-targets.json')) 'axml strip'

# ---------------------------------------------------------------- 2. sources
Step '2/6 stage strict Java sources (cloud placeholders excluded)'
$StageSrc = Join-Path $BuildDir 'src'
Remove-Item $StageSrc -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item $JavaSrc $StageSrc -Recurse
foreach ($rel in $ExcludedSources) {
    $p = Join-Path $StageSrc $rel
    if (Test-Path $p) { Remove-Item $p -Force }
}
$javaFiles = Get-ChildItem -Path $StageSrc -Recurse -Filter *.java
Write-Host ("   {0} java files staged (excluded {1})" -f $javaFiles.Count, $ExcludedSources.Count)

# ---------------------------------------------------------------- 3. javac
Step '3/6 javac'
$ClassDir = Join-Path $BuildDir 'classes'
Remove-Item $ClassDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $ClassDir | Out-Null
Invoke-Native $JavacExe (@('-source', '8', '-target', '8', '-nowarn', '-encoding', 'UTF-8',
        '-cp', $AndroidJar, '-d', $ClassDir) + ($javaFiles | ForEach-Object { $_.FullName })) 'javac'

# ---------------------------------------------------------------- 4. d8
Step '4/6 d8'
$DexDir = Join-Path $BuildDir 'dex'
Remove-Item $DexDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $DexDir | Out-Null
$classFiles = Get-ChildItem -Path $ClassDir -Recurse -Filter *.class
Invoke-Native $JavaExe (@('-cp', $D8Jar, 'com.android.tools.r8.D8', '--min-api', '27', '--release',
        '--lib', $AndroidJar, '--output', $DexDir) + ($classFiles | ForEach-Object { $_.FullName })) 'd8'
$dexFile = Join-Path $DexDir 'classes.dex'
Assert-Path $dexFile 'classes.dex'
Write-Host ("   classes.dex: {0:N0} bytes" -f (Get-Item $dexFile).Length)

# ---------------------------------------------------------------- 5. package
Step '5/6 assemble + zipalign'
$unsigned = Join-Path $OutDir 'XTCSystemUI-strict-unsigned.apk'
Invoke-Native $PyExe @((Join-Path $Tools 'mk_strict_apk.py'), $VendorApk, $strictManifest, $dexFile, $unsigned) 'packaging'
$aligned = Join-Path $OutDir 'XTCSystemUI-strict-aligned.apk'
Invoke-Native $Zipalign @('-p', '-f', '4', $unsigned, $aligned) 'zipalign'

# ---------------------------------------------------------------- 6. sign
$signed = Join-Path $OutDir 'XTCSystemUI-strict-cloud.apk'
if (-not $SkipSign) {
    Step '6/6 apksigner + no-cloud scan'
    Assert-Path $KeyStore 'keystore (run rebuild\tools\build.ps1 once)'
    Invoke-Native $JavaExe @('-jar', $ApkSignerJar, 'sign', '--ks', $KeyStore, '--ks-pass', 'pass:android',
        '--key-pass', 'pass:android', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true',
        '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', $signed, $aligned) 'apksigner'
    Write-Host ("   signed apk: {0:N0} bytes -> {1}" -f (Get-Item $signed).Length, $signed)
    Step 'static no-cloud scan'
    & $PyExe (Join-Path $Tools 'check_nocloud.py') $signed --report (Join-Path $CloudRoot 'NO-CLOUD-REPORT.md')
    if ($LASTEXITCODE -ne 0) { Write-Host '   WARNING: no-cloud scan reported residue (see NO-CLOUD-REPORT.md)' -ForegroundColor Yellow }
}
Write-Host "`nDONE. strict apk: $signed" -ForegroundColor Green
