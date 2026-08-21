#!/system/bin/sh
# ex2_backlight.sh — root daemon (รันโดย init service) คุม backlight ตาม flag ที่แอพเขียน
# เหตุผล: แอพ system_app โดน SELinux บล็อก search sysfs_leds เขียน node เองไม่ได้
#         daemon นี้รันใน context root/su -> เขียน node ได้ -> แอพแค่เขียน flag file ของตัวเอง
# flag: /data/data/com.geely.ex2.tools/files/ex2_bl
#   "0"      = ดับ backlight (ย้ำเขียน 0 ทุก tick กัน DisplayPowerController ดันคืน)
#   ตัวเลข>0 = คืนค่านั้น (ตอนปลุก) เขียนครั้งเดียว แล้วปล่อยให้ระบบคุมปกติ
NODE=/sys/class/leds/lcd-backlight/brightness
FLAG=/data/data/com.geely.ex2.tools/files/ex2_bl
last=x
while true; do
  if [ -f "$FLAG" ]; then
    v=$(cat "$FLAG" 2>/dev/null)
  else
    v=""
  fi
  if [ "$v" = "0" ]; then
    echo 0 > "$NODE" 2>/dev/null          # ย้ำ 0 ทุก tick (ชนะ controller)
  elif [ -n "$v" ] && [ "$v" != "$last" ]; then
    echo "$v" > "$NODE" 2>/dev/null        # ปลุก: เขียนค่าที่บันทึกครั้งเดียว
  fi
  last="$v"
  sleep 0.3
done
