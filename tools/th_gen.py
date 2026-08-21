# -*- coding: utf-8 -*-
# สร้าง values-th/strings.xml จากชุดสตริงแอป (แปล EN->TH) + เติม th ใน locales_config
import io, re, sys, os
RES = sys.argv[1]  # .../car_res/res

def load(p):
    s = io.open(p, encoding='utf-8').read()
    d = {}
    for m in re.finditer(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', s, re.S):
        d[m.group(1)] = (m.group(3), m.group(2))
    return d

de = load(RES + "/values/strings.xml")
en = load(RES + "/values-en/strings.xml")

def is_vn(t):
    return any(ord(c) > 127 for c in t)

appkeys = [k for k, (v, a) in de.items() if is_vn(v)]

# Thai translations (EN->TH). ไม่มี = fallback EN. คง placeholder %1$s %%.
# ไม่แปล: AVAS/VHAL/Regen/IP/Wi-Fi/SOC/Flyme/CentralEXAuto/Telegram/CONTROL_CAR_WINDOWS
TH = {
"ambient_light_current_title": "ปัจจุบัน", "ambient_light_current_unavailable": "ไม่พร้อมใช้งาน",
"ambient_light_end_time_title": "เวลาปิดไฟ", "ambient_light_header_off": "ปิด", "ambient_light_header_on": "เปิด",
"ambient_light_info_content_description": "ความช่วยเหลือไฟ ambient",
"ambient_light_info_text": "\"ส่วนนี้ควบคุมไฟ ambient ในห้องโดยสาร — แสงนุ่มบนแผงหน้าปัด ประตู และโซนอื่น ๆ\n\nอัตโนมัติ — ไฟเปิดและปิดตามเวลาที่ตั้งไว้ กำหนด «เวลาเปิดไฟ» และ «เวลาปิดไฟ» ตารางเวลาทำงานเบื้องหลังแม้ปิดแอปหรือจอรีบูต โหมดนี้จะถูกจดจำ\n\nปิด และ เปิด — ไฟปิดตลอดหรือเปิดตลอด โหมดนี้อยู่จนกว่าจะเปิดแอปใหม่ ครั้งถัดไปที่เปิดส่วนนี้ แอปจะแสดงสถานะไฟปัจจุบันของรถ\"",
"ambient_light_info_title": "หลักการทำงาน", "ambient_light_mode_auto": "อัตโนมัติ", "ambient_light_mode_off": "ปิด", "ambient_light_mode_on": "เปิด",
"ambient_light_mode_summary_off": "ไฟปิดตลอด", "ambient_light_mode_summary_on": "ไฟเปิดตลอด", "ambient_light_mode_title": "โหมด",
"ambient_light_restore_channel": "ไฟ ambient", "ambient_light_restore_notification_text": "กำลังใช้โหมดไฟ", "ambient_light_restore_notification_title": "ระบบไฟ",
"ambient_light_schedule_active_off": "ปิดตามตาราง", "ambient_light_schedule_active_on": "เปิดตามตาราง", "ambient_light_schedule_channel": "ไฟ ambient",
"ambient_light_schedule_notification_text": "เปิด-ปิดอัตโนมัติ", "ambient_light_schedule_notification_title": "ตารางเวลาไฟ", "ambient_light_screen_title": "ไฟ ambient",
"ambient_light_section_control": "ควบคุม", "ambient_light_section_schedule": "ตารางเวลา", "ambient_light_section_status": "สถานะ",
"ambient_light_start_time_title": "เวลาเปิดไฟ", "ambient_light_status_error": "อ่านข้อมูลผิดพลาด (%1$s)", "ambient_light_status_ok": "อ่านสำเร็จ (%1$s)",
"ambient_light_status_ok_schedule": "อ่านสำเร็จ (%1$s), ตาราง: %2$s", "ambient_light_status_title": "สถานะ", "ambient_light_status_write_error": "เขียนข้อมูลผิดพลาด: %1$s",
"ambient_light_time_picker_cancel": "ยกเลิก", "ambient_light_time_picker_confirm": "ยืนยัน", "ambient_light_toggle_summary_off": "ปิด", "ambient_light_toggle_summary_on": "เปิด",
"ambient_light_toggle_title": "ระบบไฟ", "ambient_light_write_error_unknown": "ข้อผิดพลาดไม่ทราบสาเหตุ",
"auto_window_channel": "ประตูและกระจก", "auto_window_corner_fl": "ประตูหน้าซ้าย", "auto_window_corner_fr": "ประตูหน้าขวา", "auto_window_corner_rl": "ประตูหลังซ้าย", "auto_window_corner_rr": "ประตูหลังขวา",
"auto_window_corner_summary": "%1$s · กระจกเปิด %2$d%%", "auto_window_corner_summary_closed": "%1$s · กระจกปิดสนิท", "auto_window_corner_summary_fully_open": "%1$s · กระจกเปิดสุด", "auto_window_corner_summary_no_window": "%1$s · อ่านกระจกไม่ได้",
"auto_window_door_closed": "ประตูปิดแล้ว", "auto_window_door_open": "ประตูเปิดอยู่",
"auto_window_enable_summary": "เปิดประตูไหนจะลดกระจกบานนั้นลง %1$d%%; ปิดประตูจะปิดกระจกกลับหากเปิดค้างไว้ ≤ %2$d%% ข้ามการทำงานขณะรถวิ่ง",
"auto_window_enable_title": "แง้มกระจกเมื่อเปิดประตู",
"auto_window_footer_system": "การสั่งกระจกต้องใช้ build ระบบ (สิทธิ์ CONTROL_CAR_WINDOWS); build ธรรมดาดูได้แค่สถานะ",
"auto_window_notification_text": "กำลังเฝ้าดูสถานะประตู", "auto_window_notification_title": "ประตูและกระจก", "auto_window_screen_title": "ประตูและกระจก",
"auto_window_section_control": "อัตโนมัติ", "auto_window_section_status": "สถานะ", "auto_window_value_unavailable": "อ่านไม่ได้",
"avas_current_title": "โหมดปัจจุบัน", "avas_header_hint": "ระบบเสียงเตือนคนเดินถนน (AVAS)", "avas_header_muted": "ปิดเสียง AVAS แล้ว",
"avas_mode_off": "ปิด", "avas_mode_type_1": "แบบ 1", "avas_mode_type_2": "แบบ 2", "avas_mode_type_3": "แบบ 3", "avas_mode_unavailable": "โหมดไม่พร้อมใช้งาน", "avas_mode_unknown": "โหมดไม่ทราบ (%1$d)",
"avas_mute_button": "ปิดเสียง AVAS", "avas_mute_confirm_message": "แน่ใจหรือไม่ว่าต้องการปิดเสียง AVAS? นี่เป็นฟีเจอร์ความปลอดภัยของรถ", "avas_mute_confirm_no": "ไม่", "avas_mute_confirm_title": "ปิดเสียง AVAS", "avas_mute_confirm_yes": "ใช่",
"avas_mute_summary_off": "ปิดการทำงาน AVAS / ลำโพงภายนอกทั้งหมด", "avas_mute_summary_on": "ปิดเสียงเตือนคนเดินถนน; คงอยู่หลังรีบูต", "avas_mute_title": "ปิดเสียง AVAS",
"avas_restore_notification_text": "กำลังคืนค่าปิดเสียงหลังบูต", "avas_section_control": "ควบคุม", "avas_section_status": "สถานะ", "avas_section_volume": "ระดับเสียง",
"avas_segment_off": "ปิด", "avas_segment_on": "เปิด", "avas_status_error": "อ่านข้อมูลผิดพลาด (%1$s)",
"avas_status_need_system": "ไม่มีสิทธิ์จัดการ AVAS (ต้องใช้ build system เช่น CentralEXAuto) รัน scripts\\build-system-apk.ps1 แล้วติดตั้ง system APK",
"avas_status_ok": "อ่านสำเร็จ (%1$s)", "avas_status_ok_muted": "บันทึกปิดเสียงแล้ว, อ่านสำเร็จ (%1$s)", "avas_status_title": "สถานะ", "avas_status_write_error": "เขียนข้อมูลผิดพลาด: %1$s",
"avas_supported_no": "ไม่ / ไม่ทราบ", "avas_supported_title": "รองรับ API", "avas_supported_yes": "ใช่", "avas_unmute_button": "เปิดเสียง AVAS",
"avas_volume_down": "ลดเสียง AVAS", "avas_volume_summary": "ระดับ %1$d / %2$d", "avas_volume_title": "ระดับเสียง AVAS", "avas_volume_unavailable": "อ่านระดับเสียงไม่ได้ (ต้องใช้ build system)", "avas_volume_up": "เพิ่มเสียง AVAS",
"avas_write_error_unknown": "ข้อผิดพลาดไม่ทราบสาเหตุ",
"battery_app_widget_description": "เปอร์เซ็นต์แบตเตอรี่ปัจจุบัน (VHAL)", "battery_app_widget_label": "เปอร์เซ็นต์แบต",
"battery_enable_summary_off": "ปิดบริการและไอคอนสถานะแล้ว", "battery_enable_summary_on": "เปิดบริการและไอคอนสถานะแล้ว", "battery_enable_title": "แสดงแบตเตอรี่",
"battery_header_off": "ปิดแบตเตอรี่", "battery_latest_disabled": "ไม่มีข้อมูล (ฟีเจอร์ถูกปิด)", "battery_latest_title": "แบตปัจจุบัน", "battery_latest_unavailable": "ไม่มีข้อมูลแบต",
"battery_section_control": "ควบคุม", "battery_section_icon": "ไอคอน", "battery_section_status": "สถานะ", "battery_service_channel": "บริการแบต", "battery_service_notification_text": "กำลังอัปเดตไอคอนแบต", "battery_service_notification_title": "บริการแบต",
"battery_status_disabled": "ปิดโดยผู้ใช้", "battery_status_error": "อ่านข้อมูลผิดพลาด (%1$s)", "battery_status_icon_channel": "ไอคอนแบต", "battery_status_icon_text_unavailable": "ไม่มีข้อมูลแบต", "battery_status_ok": "อ่านสำเร็จ (%1$s)", "battery_status_title": "สถานะ", "battery_status_waiting": "กำลังรอข้อมูล",
"battery_widget_position_step_left": "ย้ายไปซ้าย", "battery_widget_position_step_right": "ย้ายไปขวา", "battery_widget_position_summary_rank": "ตำแหน่ง %1$d — เลขน้อยอยู่ซ้าย เลขมากอยู่ขวา", "battery_widget_position_title": "ตำแหน่งบนแถบสถานะ",
"car_link_connected_bluetooth": "เชื่อมต่อโทรศัพท์ผ่าน Bluetooth: %1$s", "car_link_connected_wifi": "เชื่อมต่อโทรศัพท์ผ่าน WiFi: %1$s", "car_link_disconnected_bluetooth": "ตัดการเชื่อมต่อ Bluetooth: %1$s", "car_link_disconnected_wifi": "ตัดการเชื่อมต่อ WiFi: %1$s",
"developer_about_summary": "คำถาม ข้อเสนอแนะ และการสนับสนุน — ติดต่อผ่าน Telegram",
"driving_current_title": "การขับขี่", "driving_current_unavailable": "ไม่มีโหมด", "driving_current_unknown": "โหมดไม่ทราบ (%1$s)",
"driving_mode_adaptive": "อัจฉริยะ", "driving_mode_pure": "ไฟฟ้าล้วน", "driving_mode_snow": "หิมะ", "driving_mode_switch_title": "โหมด", "driving_persist_title": "จดจำโหมด",
"driving_raw_title": "โหมด VHAL", "driving_raw_unavailable": "ไม่มีข้อมูล", "driving_regen_current_title": "Regen", "driving_regen_current_unavailable": "ไม่มีการหน่วงคืนพลังงาน", "driving_regen_current_unknown": "ระดับไม่ทราบ (%1$s)",
"driving_regen_low": "ต่ำ", "driving_regen_mid": "กลาง", "driving_regen_persist_title": "จดจำ Regen", "driving_regen_raw_title": "Regen VHAL", "driving_regen_saved_title": "Regen ที่บันทึก",
"driving_regen_status_ok_persist": "อ่านสำเร็จ, บันทึกระดับแล้ว (%1$s)", "driving_regen_status_title": "สถานะ Regen", "driving_regen_switch_summary": "ต่ำ, กลาง, สูง", "driving_regen_switch_title": "ระดับ",
"driving_restore_channel": "โหมดการขับขี่", "driving_restore_notification_text": "กำลังคืนค่าโหมดและ Regen", "driving_restore_notification_title": "การขับขี่", "driving_saved_title": "โหมดที่บันทึก", "driving_screen_title": "การขับขี่",
"driving_section_mode": "โหมดการขับขี่", "driving_section_regen": "การหน่วงคืนพลังงาน (Regen)", "driving_section_status": "สถานะ", "driving_status_error": "อ่านข้อมูลผิดพลาด (%1$s)", "driving_status_ok": "อ่านสำเร็จ (%1$s)", "driving_status_ok_persist": "อ่านสำเร็จ, บันทึกโหมดแล้ว (%1$s)", "driving_status_title": "สถานะโหมดขับ", "driving_status_write_error": "เขียนข้อมูลผิดพลาด: %1$s", "driving_write_error_unknown": "ข้อผิดพลาดไม่ทราบสาเหตุ",
"home_about_close": "ปิด", "home_about_content_description": "เกี่ยวกับแอป", "home_about_developer": "ผู้พัฒนา", "home_about_title": "เกี่ยวกับแอป", "home_about_version": "เวอร์ชัน %1$s", "home_access_footer": "การเข้าถึงและข้อมูล", "home_tools_title": "เครื่องมือ",
"nav_back": "ย้อนกลับ", "nav_home": "หน้าหลัก",
"settings_about_contact": "ติดต่อ", "settings_about_version": "เวอร์ชัน", "settings_language_summary": "ตามภาษาระบบ หรือเลือกภาษาที่รองรับ", "settings_language_system": "ระบบ", "settings_language_title": "ภาษาแอป",
"settings_phone_link_idle": "ยังไม่มีอุปกรณ์", "settings_phone_link_state_off": "ว่าง", "settings_phone_link_state_on": "เชื่อมต่ออยู่", "settings_screen_title": "ตั้งค่า", "settings_section_about": "เกี่ยวกับแอป", "settings_section_language": "ภาษา", "settings_section_phone_link": "การเชื่อมต่อโทรศัพท์",
"speed_enable_summary_off": "ปิดบริการและไอคอนสถานะแล้ว", "speed_enable_summary_on": "เปิดบริการและไอคอนสถานะแล้ว", "speed_enable_title": "แสดงความเร็ว", "speed_header_off": "ปิดมาตรวัดความเร็ว", "speed_latest_disabled": "ไม่มีข้อมูล (ฟีเจอร์ถูกปิด)", "speed_latest_title": "ความเร็วปัจจุบัน", "speed_latest_unavailable": "ไม่มีข้อมูลความเร็ว", "speed_screen_title": "มาตรวัดความเร็ว",
"speed_section_control": "ควบคุม", "speed_section_icon": "ไอคอน", "speed_section_status": "สถานะ", "speed_service_channel": "บริการความเร็ว", "speed_service_notification_text": "กำลังอัปเดตไอคอนความเร็ว", "speed_service_notification_title": "บริการมาตรวัดความเร็ว", "speed_status_disabled": "ปิดโดยผู้ใช้", "speed_status_error": "อ่านข้อมูลผิดพลาด (%1$s)", "speed_status_icon_channel": "ไอคอนความเร็ว", "speed_status_icon_text_unavailable": "ไม่มีข้อมูลความเร็ว", "speed_status_icon_title": "ความเร็ว", "speed_status_ok": "อ่านสำเร็จ (%1$s)", "speed_status_title": "สถานะ", "speed_status_waiting": "กำลังรอข้อมูล",
"speed_widget_position_step_left": "ย้ายไปซ้าย", "speed_widget_position_step_right": "ย้ายไปขวา", "speed_widget_position_summary_rank": "ตำแหน่ง %1$d — เลขน้อยอยู่ซ้าย เลขมากอยู่ขวา", "speed_widget_position_title": "ตำแหน่งบนแถบสถานะ",
"temperature_enable_summary_off": "ปิดบริการและไอคอนสถานะแล้ว", "temperature_enable_summary_on": "เปิดบริการและไอคอนสถานะแล้ว", "temperature_enable_title": "แสดงอุณหภูมิ", "temperature_header_off": "ปิดอุณหภูมิ", "temperature_latest_disabled": "ไม่มีข้อมูล (ฟีเจอร์ถูกปิด)", "temperature_latest_title": "อุณหภูมิล่าสุด", "temperature_latest_unavailable": "ไม่มีข้อมูลอุณหภูมิ", "temperature_latest_value": "%1$d °C", "temperature_screen_title": "อุณหภูมิ",
"temperature_section_control": "ควบคุม", "temperature_section_icon": "ไอคอน", "temperature_section_status": "สถานะ", "temperature_service_channel": "บริการอุณหภูมิ", "temperature_service_notification_text": "กำลังอัปเดตไอคอนอุณหภูมิ", "temperature_service_notification_title": "บริการอุณหภูมิ", "temperature_status_disabled": "ปิดโดยผู้ใช้", "temperature_status_error": "อ่านข้อมูลผิดพลาด (%1$s)", "temperature_status_icon_channel": "ไอคอนอุณหภูมิ", "temperature_status_icon_text_available": "ภายนอก: %1$d °C", "temperature_status_icon_text_unavailable": "ไม่มีข้อมูลอุณหภูมิ", "temperature_status_icon_title": "อุณหภูมิภายนอก", "temperature_status_ok": "อ่านสำเร็จ (%1$s)", "temperature_status_title": "สถานะ", "temperature_status_waiting": "กำลังรอข้อมูล",
"temperature_widget_position_step_left": "ย้ายไปซ้าย", "temperature_widget_position_step_right": "ย้ายไปขวา", "temperature_widget_position_summary_rank": "ตำแหน่ง %1$d — เลขน้อยอยู่ซ้าย เลขมากอยู่ขวา", "temperature_widget_position_title": "ตำแหน่งบนแถบสถานะ",
"tool_ambient_light_description": "ตารางเวลาเปิด-ปิดไฟ ambient", "tool_ambient_light_title": "ไฟ ambient", "tool_auto_window_description": "แง้มกระจกเมื่อเปิดประตู ปิดกระจกเมื่อปิดประตู", "tool_auto_window_title": "ประตูและกระจก", "tool_avas_description": "ปิดเสียงเตือนคนเดินถนน (AVAS)", "tool_battery_description": "เปอร์เซ็นต์แบต (SOC) บนแถบสถานะ Flyme",
"tool_driving_description": "โหมดขับและ Regen — จดจำอัตโนมัติหลังจอกลับมาทำงาน", "tool_driving_title": "การขับขี่", "tool_settings_description": "ภาษาแอปและการตั้งค่า", "tool_settings_title": "ตั้งค่า", "tool_speed_description": "ความเร็วบนแถบสถานะ Flyme", "tool_speed_title": "มาตรวัดความเร็ว", "tool_temperature_description": "อุณหภูมิภายนอกบนแถบสถานะ Flyme", "tool_temperature_title": "อุณหภูมิ", "tool_wifi_description": "เปิด Wi-Fi และไอคอนบนแถบสถานะ Flyme",
"welcome_subtitle": "เครื่องมือตั้งค่าสำหรับ Geely EX2",
"wifi_auto_enable_summary": "เปิด Wi-Fi เมื่อระบบเริ่มทำงาน", "wifi_auto_enable_title": "เปิดอัตโนมัติ", "wifi_header_off": "ปิด Wi-Fi", "wifi_ip_address_title": "ที่อยู่ IP", "wifi_ip_address_unavailable": "ยังไม่เชื่อมต่อ", "wifi_section_control": "ควบคุม", "wifi_section_icon": "ไอคอน", "wifi_section_info": "ข้อมูล", "wifi_service_channel": "บริการสถานะ Wi-Fi", "wifi_service_notification_text": "กำลังอัปเดตไอคอนสถานะ Wi-Fi", "wifi_service_notification_title": "บริการสถานะ Wi-Fi",
"wifi_state_disabled": "ปิด", "wifi_state_disabling": "กำลังปิด", "wifi_state_enabled": "เปิด", "wifi_state_enabling": "กำลังเปิด", "wifi_state_unknown": "ไม่ทราบ", "wifi_status_icon_channel": "ไอคอนสถานะ Wi-Fi", "wifi_status_icon_text": "ไอคอนสถานะ Wi-Fi", "wifi_toggle_summary_off": "ปิดเครือข่ายไร้สายแล้ว", "wifi_toggle_summary_on": "สถานะ: %1$s",
"wifi_widget_position_step_left": "ย้ายไปซ้าย", "wifi_widget_position_step_right": "ย้ายไปขวา", "wifi_widget_position_summary_rank": "ตำแหน่ง %1$d — เลขน้อยอยู่ซ้าย เลขมากอยู่ขวา", "wifi_widget_position_title": "ตำแหน่งบนแถบสถานะ",
}

def esc(t):
    return re.sub(r'&(?!amp;|lt;|gt;|#)', '&amp;', t)

out = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
cov = 0
total = 0
for k in appkeys:
    if k.startswith('abc_'):
        continue
    total += 1
    src_en = en.get(k, (de[k][0], ''))[0]
    val = TH.get(k, src_en)
    if k in TH:
        cov += 1
    out.append('    <string name="%s">%s</string>' % (k, esc(val)))
out.append('</resources>')
os.makedirs(RES + "/values-th", exist_ok=True)
io.open(RES + "/values-th/strings.xml", 'w', encoding='utf-8').write('\n'.join(out))
print("เขียน values-th: %d คีย์ | แปลไทย %d | fallback EN %d" % (total, cov, total - cov))

lc = RES + "/xml/locales_config.xml"
s = io.open(lc, encoding='utf-8').read()
if 'name="th"' not in s:
    s = s.replace('<locale android:name="en" />', '<locale android:name="en" />\n    <locale android:name="th" />')
    io.open(lc, 'w', encoding='utf-8').write(s)
print("locales_config th:", 'name="th"' in io.open(lc, encoding='utf-8').read())
