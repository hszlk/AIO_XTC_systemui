param(
    [string]$Zip = 'D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-v82-sysui-shell.zip'
)
$ErrorActionPreference = 'Continue'
$want = (Get-FileHash $Zip -Algorithm MD5).Hash.ToLower()
Write-Host "local md5 = $want"

function Ensure-Device([int]$tries = 15) {
    for ($i = 1; $i -le $tries; $i++) {
        $st = ''
        try { $st = (& adb get-state 2>$null | Out-String).Trim() } catch { $st = '' }
        if ($st -eq 'device') { return $true }
        try { & adb reconnect offline 2>&1 | Out-Null } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Get-RemoteMd5([string]$path) {
    for ($i = 1; $i -le 10; $i++) {
        if (-not (Ensure-Device 8)) { continue }
        $out = ''
        try { $out = (& adb shell "md5sum $path 2>/dev/null" 2>$null | Out-String) } catch { $out = '' }
        if ($out -match '([0-9a-f]{32})') { return $matches[1] }
        try { & adb reconnect offline 2>&1 | Out-Null } catch {}
        Start-Sleep -Seconds 2
    }
    return ''
}

# the adb link drops right after a transfer, so every step re-arms the device
if (-not (Ensure-Device)) { Write-Host 'no device'; exit 1 }
$pushOk = $false
for ($i = 1; $i -le 12 -and -not $pushOk; $i++) {
    if (-not (Ensure-Device 8)) { Write-Host "push $i : no device"; continue }
    $r = (& adb push $Zip /sdcard/XTCSystemUI-v82.zip 2>&1 | Out-String)
    $sent = ($r -match '1 file pushed')
    Write-Host ("push {0}: sent={1}" -f $i, $sent)
    if (-not $sent) { Start-Sleep -Seconds 2; continue }
    # verify after re-arming the link
    $got = Get-RemoteMd5 '/sdcard/XTCSystemUI-v82.zip'
    Write-Host ("push {0}: remote md5 = {1}" -f $i, $got)
    if ($got -eq $want) { $pushOk = $true }
}
if (-not $pushOk) { Write-Host 'PUSH FAILED'; exit 2 }
Write-Host 'ZIP VERIFIED ON DEVICE'

if (-not (Ensure-Device)) { Write-Host 'no device for install'; exit 3 }
$res = (& adb shell "su -c 'magisk --install-module /sdcard/XTCSystemUI-v82.zip'" 2>&1 | Out-String)
Write-Host $res

for ($i = 1; $i -le 6; $i++) {
    if (-not (Ensure-Device 8)) { continue }
    $files = (& adb shell "su -c 'find /data/adb/modules_update/XTCSystemUI_HS -type f | sort'" 2>&1 | Out-String)
    if ($files -match 'XTCSystemUI.apk') {
        Write-Host $files
        $apkMd5 = Get-RemoteMd5 '/data/adb/modules_update/XTCSystemUI_HS/system/priv-app/XTCSystemUI/XTCSystemUI.apk'
        Write-Host "staged apk md5 = $apkMd5"
        break
    }
    Start-Sleep -Seconds 2
}
