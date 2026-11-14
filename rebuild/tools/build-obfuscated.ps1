#requires -Version 5.1
<#
.SYNOPSIS
    Build the R8-obfuscated ("hardened") variant of the XTCSystemUI replica.

.DESCRIPTION
    Same Mode A packaging as tools\build.ps1 (vendor resources byte identical),
    but the dex step is R8 with name obfuscation instead of plain d8:

      javac -> R8 (-dontshrink/-dontoptimize, keep rules for manifest/layout/
      reflection names) -> classes.dex -> apk-pack -> zipalign -> apksigner

    Output:
      build\XTCSystemUI-hardened-unsigned.apk
      build\XTCSystemUI-hardened.apk          (zipaligned + v1/v2/v3 signed)

    Names that the Android framework resolves from strings (manifest components,
    config_statusBarComponent, config_systemUIServiceComponents, custom view
    class names inside the vendor layout XML) are kept by
    tools\proguard-rules.pro; everything else is renamed.

.PARAMETER Clean
    Remove intermediates of this script first.
#>
param(
    [switch]$Clean,
    [switch]$SkipVerify
)

$script:ErrorActionPreference = 'Continue'
$Root = 'D:\DEBUG\apk\sysui'
$RebuildRoot = Join-Path $Root 'rebuild'
$Tools = Join-Path $RebuildRoot 'tools'
$BuildDir = Join-Path $RebuildRoot 'build'
$ObjDir = Join-Path $BuildDir 'obj-hardened'
$JavaSrc = Join-Path $Root 'src\main\java'
$VendorApk = Join-Path $Root 'XTCSystemUI.apk'

$SdkRoot = 'C:\Users\10413\AppData\Local\Android\Sdk'
$BuildTools = Join-Path $SdkRoot 'build-tools\30.0.3'
$DexBuildTools = Join-Path $SdkRoot 'build-tools\36.0.0'
$AndroidJar = Join-Path $SdkRoot 'platforms\android-30\android.jar'
$Zipalign = Join-Path $BuildTools 'zipalign.exe'
$R8Jar = Join-Path $DexBuildTools 'lib\d8.jar'
$ApkSignerJar = Join-Path $BuildTools 'lib\apksigner.jar'
$KeyStore = Join-Path $RebuildRoot 'keystore\debug.keystore'
$PackScript = Join-Path $Tools 'apk-pack.ps1'
$Rules = Join-Path $Tools 'proguard-rules.pro'
$VerifyScript = Join-Path $Tools 'verify.ps1'

$JavaExe = (Get-Command java).Source
$JavacExe = (Get-Command javac).Source

function Step([string]$text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }
function Invoke-Native([string]$exe, [string[]]$arguments, [string]$title) {
    $errFile = Join-Path $ObjDir 'stderr.txt'
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
    Remove-Item $ObjDir -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $BuildDir, $ObjDir | Out-Null

# ---------------------------------------------------------------- 1. javac
Step '1/6 javac'
$ClassDir = Join-Path $ObjDir 'classes'
Remove-Item $ClassDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $ClassDir | Out-Null
$javaFiles = Get-ChildItem -Path $JavaSrc -Recurse -Filter *.java
Write-Host ("   {0} java files" -f $javaFiles.Count)
Invoke-Native $JavacExe (@('-source', '8', '-target', '8', '-nowarn', '-encoding', 'UTF-8',
        '-cp', $AndroidJar, '-d', $ClassDir) + ($javaFiles | ForEach-Object { $_.FullName })) 'javac'
$classFiles = Get-ChildItem -Path $ClassDir -Recurse -Filter *.class
Write-Host ("   {0} class files" -f $classFiles.Count)

# ---------------------------------------------------------------- 2. R8
Step '2/6 R8 (obfuscation)'
$DexDir = Join-Path $ObjDir 'dex'
Remove-Item $DexDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $DexDir | Out-Null
Invoke-Native $JavaExe (@('-cp', $R8Jar, 'com.android.tools.r8.R8',
        '--release', '--min-api', '27', '--lib', $AndroidJar,
        '--pg-conf', $Rules, '--output', $DexDir) +
        ($classFiles | ForEach-Object { $_.FullName })) 'r8'
$dexFile = Join-Path $DexDir 'classes.dex'
if (-not (Test-Path $dexFile)) { throw 'R8 produced no classes.dex' }
Write-Host ("   classes.dex: {0:N0} bytes" -f (Get-Item $dexFile).Length)

# ---------------------------------------------------------------- 3. pack
Step '3/6 apk-pack (Mode A)'
$unsigned = Join-Path $BuildDir 'XTCSystemUI-hardened-unsigned.apk'
& $PackScript -SourceApk $VendorApk -DexFile $dexFile -OutputApk $unsigned | ForEach-Object { Write-Host "   $_" }
if ($LASTEXITCODE -ne 0) { throw 'apk-pack failed' }

# ---------------------------------------------------------------- 4. align
Step '4/6 zipalign'
$aligned = Join-Path $ObjDir 'XTCSystemUI-hardened-aligned.apk'
Invoke-Native $Zipalign @('-p', '-f', '4', $unsigned, $aligned) 'zipalign'

# ---------------------------------------------------------------- 5. sign
Step '5/6 apksigner'
$signed = Join-Path $BuildDir 'XTCSystemUI-hardened.apk'
Invoke-Native $JavaExe @('-jar', $ApkSignerJar, 'sign', '--ks', $KeyStore, '--ks-pass', 'pass:android',
    '--key-pass', 'pass:android', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true',
    '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', $signed, $aligned) 'apksigner'
Write-Host ("   signed apk: {0:N0} bytes -> {1}" -f (Get-Item $signed).Length, $signed)

# ---------------------------------------------------------------- 6. verify
if (-not $SkipVerify -and (Test-Path $VerifyScript)) {
    Step '6/6 verify.ps1 (parity checks)'
    & $VerifyScript -SignedApk $signed -OriginalApk $VendorApk
    $verifyExit = $LASTEXITCODE
    if ($verifyExit -ne 0) { throw "verify failed (exit $verifyExit)" }
}

Write-Host "`nDONE. hardened apk: $signed" -ForegroundColor Green
