param(
    [string]$Zip = 'D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-v82-sysui-shell.zip',
    [string]$Apk = 'D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-hardened.apk'
)
$ErrorActionPreference = 'Continue'
$zipMd5 = (Get-FileHash $Zip -Algorithm MD5).Hash.ToLower()
$apkMd5 = (Get-FileHash $Apk -Algorithm MD5).Hash.ToLower()
Write-Host "zip=$zipMd5 apk=$apkMd5"

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

function Sh([string]$cmd, [int]$tries = 6) {
    for ($i = 1; $i -le $tries; $i++) {
        if (-not (Ensure-Device 10)) { continue }
        $out = ''
        try { $out = (& adb shell $cmd 2>&1 | Out-String) } catch { $out = '' }
        if ($out.Trim().Length -gt 0) { return $out }
        Start-Sleep -Seconds 1
    }
    return ''
}

function PushVerified([string]$local, [string]$remote, [string]$expect, [int]$tries = 20) {
    for ($i = 1; $i -le $tries; $i++) {
        if (-not (Ensure-Device 10)) { continue }
        $r = (& adb push $local $remote 2>&1 | Out-String)
        if ($r -notmatch '1 file pushed') { Start-Sleep -Seconds 1; continue }
        $got = (Sh "md5sum $remote 2>/dev/null")
        if ($got -match '([0-9a-f]{32})') {
            $h = $matches[1]
            Write-Host ("  push try {0}: {1}" -f $i, $h)
            if ($h -eq $expect) { return $true }
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

if (-not (Ensure-Device)) { Write-Host 'NO DEVICE'; exit 1 }

Write-Host '--- 1. install module ---'
if (PushVerified $Zip '/sdcard/XTCSystemUI-v82.zip' $zipMd5) {
    Write-Host (Sh "su -c 'magisk --install-module /sdcard/XTCSystemUI-v82.zip'")
    Write-Host (Sh "su -c 'md5sum /data/adb/modules_update/XTCSystemUI_HS/system/priv-app/XTCSystemUI/XTCSystemUI.apk'")
} else {
    Write-Host 'zip push failed'
}

Write-Host '--- 2. update z overlay ---'
if (PushVerified $Apk '/sdcard/XTCSystemUI-v82.apk' $apkMd5) {
    $script = "su -c '/data/adb/magisk/busybox chattr -R -i /data/adb/modules/z 2>/dev/null; cat /sdcard/XTCSystemUI-v82.apk > /data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk; chmod 644 /data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk; /data/adb/magisk/busybox chattr +i /data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk 2>/dev/null; md5sum /data/adb/modules/z/system/priv-app/XTCSystemUI/XTCSystemUI.apk'"
    Write-Host (Sh $script)
} else {
    Write-Host 'apk push failed'
}

Write-Host '--- 3. restart SystemUI ---'
Write-Host (Sh "su -c 'kill \$(pidof com.android.systemui)'")
Start-Sleep -Seconds 6
Write-Host ("pid now: " + (Sh "pidof com.android.systemui"))
Write-Host ("live apk: " + (Sh "md5sum /system/priv-app/XTCSystemUI/XTCSystemUI.apk"))

Write-Host '--- 4. battery state ---'
Write-Host (Sh "dumpsys battery | head -14")
Write-Host ("current_now: " + (Sh "cat /sys/class/power_supply/battery/current_now"))
Write-Host ("status: " + (Sh "cat /sys/class/power_supply/battery/status"))
Write-Host ("capacity: " + (Sh "cat /sys/class/power_supply/battery/capacity"))
