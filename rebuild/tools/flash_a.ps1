$ErrorActionPreference = 'Continue'
$Zip = 'D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-v82-sysui-shell.zip'
$Apk = 'D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-hardened.apk'
$zipMd5 = (Get-FileHash $Zip -Algorithm MD5).Hash.ToLower()
$apkMd5 = (Get-FileHash $Apk -Algorithm MD5).Hash.ToLower()
Write-Host "zip=$zipMd5 apk=$apkMd5"

function Ensure-Device([int]$tries = 25) {
    for ($i = 1; $i -le $tries; $i++) {
        $st = ''
        try { $st = (& adb get-state 2>$null | Out-String).Trim() } catch { $st = '' }
        if ($st -eq 'device') { return $true }
        try { & adb reconnect offline 2>&1 | Out-Null } catch {}
        Start-Sleep -Seconds 3
    }
    return $false
}

function Sh([string]$cmd, [int]$tries = 5) {
    for ($i = 1; $i -le $tries; $i++) {
        if (-not (Ensure-Device 12)) { continue }
        $out = ''
        try { $out = (& adb shell $cmd 2>&1 | Out-String) } catch { $out = '' }
        if ($out.Trim().Length -gt 0) { return $out.Trim() }
        Start-Sleep -Seconds 1
    }
    return ''
}

function PushVerified([string]$local, [string]$remote, [string]$expect) {
    for ($i = 1; $i -le 15; $i++) {
        if (-not (Ensure-Device 10)) { continue }
        $r = (& adb push $local $remote 2>&1 | Out-String)
        if ($r -notmatch '1 file pushed') { Start-Sleep -Seconds 1; continue }
        $got = Sh "md5sum $remote 2>/dev/null"
        if ($got -match '([0-9a-f]{32})') {
            if ($matches[1] -eq $expect) { return $true }
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

if (-not (Ensure-Device)) { Write-Host 'NO DEVICE'; exit 1 }

Write-Host '=== 1/3 install module ==='
if (PushVerified $Zip '/sdcard/XTCSystemUI-v82.zip' $zipMd5) {
    Write-Host (Sh "su -c 'magisk --install-module /sdcard/XTCSystemUI-v82.zip'")
    Write-Host (Sh "su -c 'md5sum /data/adb/modules_update/XTCSystemUI_HS/system/priv-app/XTCSystemUI/XTCSystemUI.apk 2>/dev/null'")
} else { Write-Host 'zip push failed' }

Write-Host '=== 2/3 update z overlay ==='
if (PushVerified $Apk '/sdcard/XTCSystemUI-v82.apk' $apkMd5) {
    $cmd = "su -c '/data/adb/magisk/busybox chattr -R -i /data/adb/modules/z 2>/dev/null; cat /sdcard/XTCSystemUI-v82.apk > /data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk; chmod 644 /data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk; /data/adb/magisk/busybox chattr +i /data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk 2>/dev/null; md5sum /data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk'"
    Write-Host (Sh $cmd)
} else { Write-Host 'apk push failed' }

Write-Host '=== 3/3 restart SystemUI ==='
Write-Host (Sh 'su -c "kill $(pidof com.android.systemui)"')
Start-Sleep -Seconds 7
Write-Host ('pid: ' + (Sh 'pidof com.android.systemui'))
Write-Host ('live apk: ' + (Sh 'md5sum /system/priv-app/XTCSystemUI/XTCSystemUI.apk'))
Write-Host ('pkg: ' + (Sh 'pm path com.android.systemui'))
