# -*- coding: utf-8 -*-
"""
ipa_strings.py — สกัดข้อความจากไฟล์ IPA (Mach-O binary / debug dylib)
รองรับ UTF-8 (ภาษาเวียดนามหลายไบต์) และแยกคลาส SwiftUI

    py -X utf8 ipa_strings.py <path.ipa>
    py -X utf8 ipa_strings.py <path.ipa> --vietnamese
    py -X utf8 ipa_strings.py <path.ipa> --grep CarLink,Peripheral,47800

หมายเหตุ: IPA แบบ debug build เก็บโค้ดจริงใน *.debug.dylib (main binary เป็นแค่ stub)
สคริปต์นี้จะเลือกไฟล์ที่ใหญ่สุดใน .app มาแกะ
"""
import zipfile, os, re, sys

def pick_binary(z):
    cands = [(z.getinfo(n).file_size, n) for n in z.namelist()
             if re.search(r"Payload/[^/]+\.app/[^/]+$", n)
             and not n.endswith((".plist", ".png", ".car", ".nib", ".strings", ".mobileprovision", "PkgInfo"))]
    cands.sort(reverse=True)
    return cands[0][1] if cands else None

def utf8_runs(data, lo=3, hi=140):
    runs = re.findall(rb"(?:[\x20-\x7e]|[\xc2-\xf4][\x80-\xbf]{1,3}){%d,%d}" % (lo, hi), data)
    out = []
    for r in runs:
        try: out.append(r.decode("utf-8"))
        except Exception: pass
    return out

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(1)
    z = zipfile.ZipFile(sys.argv[1])
    bn = pick_binary(z)
    print(f"# binary: {bn} ({z.getinfo(bn).file_size} bytes)")
    data = z.read(bn)
    strs = sorted(set(utf8_runs(data)))
    if "--vietnamese" in sys.argv:
        VN = re.compile(r"[ăâêôơưđàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵĐ]")
        strs = [s for s in strs if VN.search(s) and 2 < len(s) < 90]
    if "--grep" in sys.argv:
        terms = sys.argv[sys.argv.index("--grep") + 1].split(",")
        strs = [s for s in strs if any(t.lower() in s.lower() for t in terms)]
    print(f"# {len(strs)} strings")
    for s in strs:
        print(s)
