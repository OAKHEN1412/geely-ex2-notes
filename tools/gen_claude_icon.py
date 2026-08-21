# -*- coding: utf-8 -*-
"""
gen_claude_icon.py — สร้างชุดไอคอน "Claude" (สไตล์ sunburst สีดินเผา/clay) สำหรับแอป ClaudeWeb
รัน:  py -X utf8 tools/gen_claude_icon.py
ได้:  claudeapp/res/  (vector drawable + adaptive icon xml + PNG mipmap ครบทุก density)

พาเลตต์ Claude:
  clay  #D97757  (สีหลัก/พื้นไอคอน)
  cream #FAF9F5  (เส้น sunburst บนพื้น clay)
  bg    #1F1E1C  (พื้น splash เข้ม)
"""
import os, math
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES  = os.path.join(ROOT, "claudeapp", "res")

CLAY  = (0xD9, 0x77, 0x57, 255)
CREAM = (0xFA, 0xF9, 0xF5, 255)

RAYS = 12  # จำนวนแฉก sunburst


def ray_pathdata(cx, cy, inner, outer):
    """สร้าง pathData (M..L..) ของทุกแฉก รวมใน path เดียว (ใช้ strokeLineCap=round)"""
    parts = []
    for i in range(RAYS):
        a = math.radians(i * (360.0 / RAYS) - 90.0)  # เริ่มจากบนสุด
        x1 = cx + inner * math.cos(a); y1 = cy + inner * math.sin(a)
        x2 = cx + outer * math.cos(a); y2 = cy + outer * math.sin(a)
        parts.append("M%.2f,%.2f L%.2f,%.2f" % (x1, y1, x2, y2))
    return " ".join(parts)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print("  +", os.path.relpath(path, ROOT).replace("\\", "/"))


def vector(color_hex, sw, inner, outer):
    """vector drawable 108x108 ของ sunburst"""
    pd = ray_pathdata(54, 54, inner, outer)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            '    <path android:strokeColor="%s" android:strokeWidth="%.1f"\n'
            '        android:strokeLineCap="round" android:pathData="%s"/>\n'
            '</vector>\n' % (color_hex, sw, pd))


# ---------- vector drawables ----------
# foreground ของ adaptive icon: อยู่ใน safe-zone (รัศมี ~33 จากจุดกลาง 54)
write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
      vector("#FAF9F5", 7.5, 5, 30))
# พื้น adaptive icon = clay ทึบ
write(os.path.join(RES, "drawable", "ic_launcher_background.xml"),
      '<?xml version="1.0" encoding="utf-8"?>\n'
      '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
      '    android:width="108dp" android:height="108dp"\n'
      '    android:viewportWidth="108" android:viewportHeight="108">\n'
      '    <path android:fillColor="#D97757" android:pathData="M0,0 H108 V108 H0 Z"/>\n'
      '</vector>\n')
# โลโก้ clay บนพื้นเข้ม (ใช้ใน splash + overlay โหลด/ออฟไลน์) — แฉกยาวกว่าเพราะไม่โดน mask
write(os.path.join(RES, "drawable", "ic_logo.xml"),
      vector("#D97757", 8.0, 6, 40))

# ---------- adaptive icon (API26+) ----------
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n')
write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), adaptive)
write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_round.xml"), adaptive)


# ---------- PNG mipmap (fallback pre-26 / launcher ที่ไม่รองรับ adaptive) ----------
def draw_burst(size, round_icon):
    ss = 4  # supersample เพื่อ antialias
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2.0
    # พื้น clay
    if round_icon:
        d.ellipse([0, 0, S - 1, S - 1], fill=CLAY)
    else:
        r = int(S * 0.14)  # มุมโค้งเล็กน้อย
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=r, fill=CLAY)
    # แฉก sunburst สีครีม (เส้นหนา วิ่งเข้าใกล้กลาง หัวนอกกลม)
    inner = S * 0.055
    outer = S * 0.40
    w = int(S * 0.072)
    for i in range(RAYS):
        a = math.radians(i * (360.0 / RAYS) - 90.0)
        x1 = c + inner * math.cos(a); y1 = c + inner * math.sin(a)
        x2 = c + outer * math.cos(a); y2 = c + outer * math.sin(a)
        d.line([x1, y1, x2, y2], fill=CREAM, width=w)
        rr = w / 2.0  # หัวกลมเฉพาะปลายนอก
        d.ellipse([x2 - rr, y2 - rr, x2 + rr, y2 + rr], fill=CREAM)
    # จุดกลางเล็ก ๆ ให้แฉกบรรจบเนียน
    d.ellipse([c - w * 0.5, c - w * 0.5, c + w * 0.5, c + w * 0.5], fill=CREAM)
    return img.resize((size, size), Image.LANCZOS)


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dens, px in DENSITIES.items():
    for name, rnd in (("ic_launcher.png", False), ("ic_launcher_round.png", True)):
        p = os.path.join(RES, "mipmap-" + dens, name)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        draw_burst(px, rnd).save(p)
        print("  +", os.path.relpath(p, ROOT).replace("\\", "/"))

print("เสร็จ: ไอคอน Claude ครบใน claudeapp/res/")
