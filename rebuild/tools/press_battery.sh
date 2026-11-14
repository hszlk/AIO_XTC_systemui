#!/system/bin/sh
# Expand the pull-down and long-press the battery icon in the status row.
echo "== expand panel =="
input swipe 160 10 160 260 220
sleep 2
echo "== long press battery (right end of the status row) =="
input swipe 300 14 300 14 1200
sleep 1
echo "== windows containing card =="
dumpsys window windows 2>/dev/null | grep -i "XTCBatteryCard\|XTCNightMask" | head -5
echo "== done =="
