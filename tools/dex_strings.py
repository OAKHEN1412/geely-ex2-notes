# -*- coding: utf-8 -*-
"""
dex_strings.py — สกัด string table จากไฟล์ APK (DEX)
ใช้แกะข้อความ UI, ชื่อคลาส, permission ฯลฯ จากแอป Android โดยไม่ต้อง decompile

    py -X utf8 dex_strings.py <path.apk>
    py -X utf8 dex_strings.py <path.apk> --classes com.geely   # กรองเฉพาะคลาสที่ขึ้นต้นด้วย ...
    py -X utf8 dex_strings.py <path.apk> --vietnamese           # เฉพาะสตริงที่มีสระเวียดนาม
"""
import zipfile, struct, os, re, sys

def uleb(b, i):
    r = 0; s = 0
    while True:
        x = b[i]; i += 1
        r |= (x & 0x7f) << s
        if not (x & 0x80): break
        s += 7
    return r, i

def dex_strings(d):
    n = struct.unpack_from("<I", d, 0x38)[0]
    off = struct.unpack_from("<I", d, 0x3C)[0]
    out = []
    for k in range(n):
        p = struct.unpack_from("<I", d, off + 4 * k)[0]
        _, q = uleb(d, p)
        e = d.index(b"\x00", q)
        out.append(d[q:e].decode("utf-8", "replace"))
    return out

def load(apk):
    z = zipfile.ZipFile(apk)
    s = []
    for n in z.namelist():
        if re.fullmatch(r"classes\d*\.dex", n):
            s += dex_strings(z.read(n))
    return s

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(1)
    apk = sys.argv[1]
    strs = sorted(set(load(apk)))
    if "--classes" in sys.argv:
        pref = sys.argv[sys.argv.index("--classes") + 1].replace(".", "/")
        strs = [s for s in strs if ("L" + pref) in s]
    if "--vietnamese" in sys.argv:
        VN = re.compile(r"[ăâêôơưđàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵĐ]")
        strs = [s for s in strs if VN.search(s) and 2 < len(s) < 90]
    print(f"# {os.path.basename(apk)} — {len(strs)} strings")
    for s in strs:
        print(s)
