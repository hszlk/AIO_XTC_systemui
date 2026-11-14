param(
    [string]$Apk = 'D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-hardened.apk'
)
$ErrorActionPreference = 'Continue'
$want = (Get-FileHash $Apk -Algorithm MD5).Hash.ToLower()
$sh   = 'D:\DEBUG\apk\sysui\rebuild\tools\update_z_apk.sh'
Write-Host "local apk md5 = $want"

function Ensure-Device([int]$tries = 20) {
    for ($i = 1; $i -le $tries; $i++) {
        $st = ''
        try { $st = (& adb get-state 2>$null | Out-String).Trim() } catch { $st = '' }
        if ($st -eq 'device') { return $true }
        try { & adb reconnect offline 2>&1 | Out-Null } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function RemoteMd5([string]$path) {
    for ($i = 1; $i -le 6; $i++) {
        if (-not (Ensure-Device 10)) { continue }
        $out = ''
        try { $out = (& adb shell "md5sum $path 2>/dev/null" 2>$null | Out-String) } catch { $out = '' }
        if ($out -match '([0-9a-f]{32})') { return $matches[1] }
        Start-Sleep -Seconds 1
    }
    return ''
}

function PushVerified([string]$local, [string]$remote, [string]$expect) {
    for ($i = 1; $i -le 15; $i++) {
        if (-not (Ensure-Device 10)) { continue }
        $r = (& adb push $local $remote 2>&1 | Out-String)
        if ($r -notmatch '1 file pushed') { Start-Sleep -Seconds 1; continue }
        $got = RemoteMd5 $remote
        Write-Host ("  push {0}: remote={1}" -f $i, $got)
        if ($got -eq $expect) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

if (-not (Ensure-Device)) { Write-Host 'no device'; exit 1 }

if (-not (PushVerified $Apk '/sdcard/XTCSystemUI-v82.apk' $want)) {
    Write-Host 'apk push failed'
    exit 2
}
Write-Host 'APK ON DEVICE OK'

if (-not (PushVerified $sh '/data/local/tmp/update_z_apk.sh' (Get-FileHash $sh -Algorithm MD5).Hash.ToLower())) {
    Write-Host 'script push failed (continuing anyway)'
}

for ($i = 1; $i -le 8; $i++) {
    if (-not (Ensure-Device 10)) { continue }
    $out = (& adb shell "su -c 'sh /data/local/tmp/update_z_apk.sh'" 2>&1 | Out-String)
    Write-Host ("  run {0}: {1}" -f $i, ($out -replace '\s+', ' '))
    if ($out -match $want) { break }
    Start-Sleep -Seconds 2
}

$zmd5 = RemoteMd5 '/data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk'
Write-Host "z overlay apk md5 = $zmd5"
if ($zmd5 -eq $want) { Write-Host 'Z OVERLAY VERIFIED' } else { Write-Host 'Z OVERLAY MISMATCH' }
