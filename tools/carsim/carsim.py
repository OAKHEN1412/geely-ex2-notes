#!/usr/bin/env python
"""carsim.py - ควบคุมรถจำลอง (SimCar) ของ OAKHEN บนเครื่องจำลอง EX2_A9 จากคอม (stdlib ล้วน)

  python -I tools/carsim/carsim.py                 # เปิดหน้าควบคุมที่ http://127.0.0.1:8765
  python -I tools/carsim/carsim.py serve --port 9000
  python -I tools/carsim/carsim.py speed 60        # CLI: speed gear key plug soc chargekw set wash reset dump trip off
  python -I tools/carsim/carsim.py gear D
  python -I tools/carsim/carsim.py key 0x41020500 --long
  python -I tools/carsim/carsim.py set 0x22010100 0 0x22010102

ทุกคำสั่ง = adb shell am broadcast -a com.oakhen.ex2dev.SIM --es cmd ... (ผู้ส่งต้องเป็น shell) · ตอบกลับเป็น JSON ใน result data
ต้องเปิดเครื่องจำลองไว้ก่อน (Desktop\\EX2 Emulator.bat) · สคริปต์นี้ไม่สตาร์ท/ลงแอพให้
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ACTION = "com.oakhen.ex2dev.SIM"
SAFE = re.compile(r"^[0-9A-Za-z_.+\-]{1,40}$")   # adb shell ต่อ argv เป็นสตริงเดียวบนเครื่อง → ห้ามมีอักขระ shell
CMDS = {"speed", "gear", "key", "plug", "soc", "chargekw", "set", "wash", "reset", "dump", "off"}
KEYS = {"left": "0x41020400", "right": "0x41020200", "up": "0x41020100", "down": "0x41020300", "voice": "0x41020500"}


def find_adb():
    p = shutil.which("adb")
    if p:
        return p
    roots = [os.environ.get("LOCALAPPDATA", "") and os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk"),
             os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    for r in roots:
        if r:
            for name in ("adb.exe", "adb"):
                c = os.path.join(r, "platform-tools", name)
                if os.path.isfile(c):
                    return c
    return None


class Adb:
    def __init__(self, adb=None, serial=None):
        self.adb = adb or find_adb()
        self.serial = serial
        self.lock = threading.Lock()

    def _run(self, args, timeout=15):
        if not self.adb:
            raise RuntimeError("ไม่พบ adb (ใส่ --adb หรือเพิ่ม %LOCALAPPDATA%\\Android\\Sdk\\platform-tools ใน PATH)")
        cmd = [self.adb] + (["-s", self.serial] if self.serial else []) + args
        r = subprocess.run(cmd, capture_output=True, timeout=timeout)
        return r.returncode, (r.stdout + r.stderr).decode("utf-8", "replace")

    def pick_serial(self):
        """มีเครื่องเดียว = ใช้เครื่องนั้น · หลายเครื่อง = เอา emulator-* ก่อน"""
        if self.serial:
            return self.serial
        _, out = self._run(["devices"])
        devs = [l.split()[0] for l in out.splitlines()[1:] if l.strip().endswith("device")]
        if not devs:
            raise RuntimeError("ไม่มีเครื่องต่อ adb (เปิดเครื่องจำลองด้วย Desktop\\EX2 Emulator.bat ก่อน)")
        emu = [d for d in devs if d.startswith("emulator-")]
        self.serial = (emu or devs)[0]
        return self.serial

    def send(self, cmd, **kw):
        """ส่งคำสั่งหนึ่งชุด → dict ที่ตอบกลับ (ok/msg หรือ dump)"""
        if cmd not in CMDS:
            return {"ok": False, "msg": "unknown cmd " + str(cmd)}
        args = ["shell", "am", "broadcast", "-a", ACTION, "--es", "cmd", cmd]
        for k, v in kw.items():
            if v is None:
                continue
            v = str(v)
            if not SAFE.match(k) or not SAFE.match(v):
                return {"ok": False, "msg": "invalid arg " + k}
            args += ["--es", k, v]
        with self.lock:
            self.pick_serial()
            rc, out = self._run(args)
        m = re.search(r'data="(.*)"\s*$', out, re.S)
        if m:
            try:
                return json.loads(m.group(1))
            except ValueError:
                return {"ok": False, "msg": "reply parse: " + m.group(1)[:80]}
        if "result=0" in out or "Broadcast completed" in out:
            return {"ok": False, "msg": "ไม่มีตัวรับ - รถจำลองยังไม่ถูกเปิด (ต้องลง OAKHEN ที่ต่อ SimCtl ใน DevService และรอบนเครื่องจำลอง) หรือ android.permission.DUMP ไม่ผ่าน"}
        return {"ok": False, "msg": out.strip()[:200] or "adb rc=%d" % rc}


def prop_val(dump, prop, area=None):
    for p in dump.get("props", []):
        if int(p["p"], 16) == prop and (area is None or p["a"] == area):
            return p["v"]
    return None


class Trip(threading.Thread):
    """ทริปทดสอบ: D -> เร่งถึง 60 -> ล่อง 2 นาที -> หยุด -> P"""
    def __init__(self, adb, cruise_s=120, kmh=60):
        super().__init__(daemon=True)
        self.adb, self.cruise_s, self.kmh = adb, cruise_s, kmh
        self.step, self.running, self.stop_flag, self.note = "พร้อม", False, False, ""

    def _wait(self, pred, timeout):
        t0 = time.time()
        while time.time() - t0 < timeout and not self.stop_flag:
            d = self.adb.send("dump")
            if d.get("ok") and pred(d):
                return True
            time.sleep(1)
        return False

    def run(self):
        self.running, self.stop_flag = True, False
        try:
            self.step = "เข้าเกียร์ D"
            if not self.adb.send("gear", v="D").get("ok"):
                self.note = "เข้า D ไม่ได้ (รถกำลังวิ่ง?)"
                return
            self.step = "เร่งถึง %d km/h" % self.kmh
            self.adb.send("speed", v=self.kmh)
            self._wait(lambda d: d["speed"] >= self.kmh - 1, 90)
            self.step = "ล่อง %d วินาที" % self.cruise_s
            t0 = time.time()
            while time.time() - t0 < self.cruise_s and not self.stop_flag:
                time.sleep(1)
            self.step = "เบรกจนหยุด"
            self.adb.send("speed", v=0)
            self._wait(lambda d: d["speed"] < 0.5, 60)
            self.step = "เข้าเกียร์ P"
            self.adb.send("gear", v="P")
            self.note = "ยกเลิกกลางทาง" if self.stop_flag else "จบทริป (จอด P ค้าง 2 นาทีเพื่อให้ TripLog ปิดทริป)"
        finally:
            if self.stop_flag:
                self.adb.send("speed", v=0)
            self.running = False
            self.step = "พร้อม"


PAGE = r"""<!doctype html><meta charset="utf-8"><title>รถจำลอง EX2</title>
<style>
body{font:15px system-ui,Segoe UI,sans-serif;background:#14161a;color:#e8e6df;margin:0;padding:18px;max-width:880px}
h1{font-size:19px;margin:0 0 4px}h2{font-size:13px;color:#9aa;margin:18px 0 6px;font-weight:600}
.card{background:#1d2026;border-radius:10px;padding:12px 14px;margin-bottom:10px}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
button{background:#2b3038;color:#e8e6df;border:0;border-radius:8px;padding:9px 14px;font:inherit;cursor:pointer}
button:hover{background:#383e48}button.on{background:#c9a24b;color:#111}
input[type=range]{flex:1;min-width:200px}input[type=number]{width:80px;background:#14161a;color:inherit;border:1px solid #444;border-radius:6px;padding:6px}
.big{font-size:34px;font-weight:700}.kv{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}.kv div{background:#14161a;border-radius:8px;padding:8px}
.kv small{display:block;color:#8a9;font-size:12px}#log{color:#9aa;font-size:13px;min-height:1.4em}#err{color:#e86;font-size:13px}
</style>
<h1>รถจำลอง Geely EX2 (SimCar)</h1><div id="err"></div>
<div class="card"><div class="kv">
<div><small>ความเร็ว</small><span class="big" id="v_speed">-</span> km/h</div>
<div><small>เกียร์</small><span class="big" id="v_gear">-</span></div>
<div><small>แบต</small><span class="big" id="v_soc">-</span> %</div>
<div><small>ไมล์รวม / วิ่งได้</small><span id="v_odo">-</span> km<br><span id="v_range">-</span> km</div>
<div><small>ชาร์จ</small><span id="v_chg">-</span></div>
<div><small>โหมดขับ / รีเจน</small><span id="v_drive">-</span> / <span id="v_regen">-</span></div>
<div><small>แอร์</small><span id="v_hvac">-</span></div>
<div><small>โหมดล้างรถ</small><span id="v_wash">-</span></div></div></div>

<h2>ความเร็ว (คันเร่ง - ขยับจริงเมื่อเกียร์ D/R)</h2>
<div class="card row"><input type="range" id="sp" min="0" max="120" value="0"><b id="spv">0</b> km/h
<button onclick="send('speed',{v:0});sp.value=0;spv.textContent=0">0</button></div>

<h2>เกียร์</h2>
<div class="card row" id="gears"></div>

<h2>แบต / ชาร์จ</h2>
<div class="card row"><input type="range" id="soc" min="0" max="100" value="80"><b id="socv">80</b> %
<button id="plug" onclick="togglePlug()">เสียบสายชาร์จ</button>
<button onclick="send('chargekw',{v:7})">AC 7 kW</button><button onclick="send('chargekw',{v:50})">DC 50 kW</button></div>

<h2>ปุ่มพวงมาลัย (กดสั้น / กดค้าง 1 วิ)</h2>
<div class="card row" id="keys"></div>

<h2>ทริปทดสอบ</h2>
<div class="card row"><button onclick="trip('start')">ขับทริป: D, เร่ง 60, ล่อง 2 นาที, หยุด, P</button><button onclick="trip('stop')">ยกเลิก</button><span id="tripst"></span></div>
<div class="card row"><button onclick="send('reset',{})">รีเซ็ตรถ</button><button onclick="send('wash',{v:'notavailable'})">ล้างรถ: ไม่รองรับ</button><button onclick="send('wash',{v:'active'})">ล้างรถ: รองรับ</button></div>
<div id="log"></div>
<script>
const $=id=>document.getElementById(id);
const KEYS=[["ซ้าย","0x41020400"],["ขวา","0x41020200"],["บน (เสียง +)","0x41020100"],["ล่าง (เสียง -)","0x41020300"],["สั่งเสียง","0x41020500"]];
const DRIVE={0x22010101:"ECO",0x22010102:"COMFORT",0x22010103:"SPORT"},REGEN={0x20020501:"ต่ำ",0x20020502:"กลาง",0x20020503:"สูง"};
let plugged=0;
for(const g of "PRND"){const b=document.createElement("button");b.textContent=g;b.id="g"+g;b.onclick=()=>send("gear",{v:g});$("gears").append(b)}
for(const [n,p] of KEYS){for(const l of [0,1]){const b=document.createElement("button");b.textContent=n+(l?" (ค้าง)":"");b.onclick=()=>send("key",{v:p,long:l});$("keys").append(b)}}
async function api(path,body){try{const r=await fetch(path,body?{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)}:{});return await r.json()}catch(e){return{ok:false,msg:String(e)}}}
async function send(cmd,args){const r=await api("/api/cmd",{cmd,args});$("log").textContent=cmd+" "+JSON.stringify(args||{})+" -> "+(r.msg||(r.ok?"ok":"fail"));if(cmd!="dump")poll()}
function togglePlug(){plugged=plugged?0:1;send("plug",{v:plugged})}
async function trip(a){const r=await api("/api/trip",{action:a});$("tripst").textContent=r.step?r.step+(r.note?" - "+r.note:""):""}
function pv(d,p,a){for(const x of d.props||[])if(parseInt(x.p,16)==p&&(a===undefined||x.a==a))return x.v;return null}
async function poll(){const d=await api("/api/cmd",{cmd:"dump",args:{}});
 $("err").textContent=d.ok?"":(d.msg||"ต่อรถจำลองไม่ได้");if(!d.ok)return;
 $("v_speed").textContent=d.speed.toFixed(0);$("v_gear").textContent=d.gear;$("v_soc").textContent=d.soc.toFixed(1);
 $("v_odo").textContent=d.odo.toFixed(1);$("v_range").textContent=d.range.toFixed(0);
 $("v_chg").textContent=d.charging?"กำลังชาร์จ "+d.chargeKw+" kW":d.plugged?"เสียบสายอยู่ (ชาร์จเมื่อ P)":"ไม่ได้เสียบ";
 plugged=d.plugged;$("plug").className=d.plugged?"on":"";
 $("v_drive").textContent=DRIVE[pv(d,0x22010100)]||"?";$("v_regen").textContent=REGEN[pv(d,0x20020500)]||"?";
 const ac=pv(d,0x15600099),t=pv(d,0x25401036,1),f=pv(d,0x15400500);
 $("v_hvac").textContent=(ac?"A/C เปิด":"A/C ปิด")+" - "+(t==null?"-":t.toFixed(1))+" C - พัดลม "+f;
 $("v_wash").textContent=d.wash.status+(d.wash.status=="active"?(d.wash.value==0x2f010201?" (ล้างอยู่)":" (ปิด)"):"");
 for(const g of "PRND")$("g"+g).className=d.gear==g?"on":"";
 if(document.activeElement!==$("sp")){}
 const t2=await api("/api/trip");$("tripst").textContent=t2.running?t2.step:(t2.note||"")}
$("sp").oninput=e=>{$("spv").textContent=e.target.value};$("sp").onchange=e=>send("speed",{v:e.target.value});
$("soc").oninput=e=>{$("socv").textContent=e.target.value};$("soc").onchange=e=>send("soc",{v:e.target.value});
poll();setInterval(poll,1500);
</script>"""


def make_handler(adb, port):
    state = {"trip": None}
    hosts = {"127.0.0.1:%d" % port, "localhost:%d" % port}

    class H(BaseHTTPRequestHandler):
        def log_message(self, *a):
            pass

        def _send(self, code, body, ctype="application/json; charset=utf-8"):
            b = body if isinstance(body, bytes) else body.encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(b)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(b)

        def _guard(self):
            """กันเว็บอื่นยิงข้าม origin เข้า localhost: ตรวจ Host + Origin"""
            if self.headers.get("Host", "") not in hosts:
                self._send(403, '{"ok":false,"msg":"bad host"}')
                return False
            o = self.headers.get("Origin")
            if o and o.split("://", 1)[-1] not in hosts:
                self._send(403, '{"ok":false,"msg":"bad origin"}')
                return False
            return True

        def _body(self):
            n = int(self.headers.get("Content-Length") or 0)
            try:
                return json.loads(self.rfile.read(n) or b"{}")
            except ValueError:
                return {}

        def _trip_json(self):
            t = state["trip"]
            return json.dumps({"running": bool(t and t.running), "step": t.step if t else "", "note": t.note if t else ""}, ensure_ascii=False)

        def do_GET(self):
            if not self._guard():
                return
            if self.path in ("/", "/index.html"):
                self._send(200, PAGE, "text/html; charset=utf-8")
            elif self.path == "/api/trip":
                self._send(200, self._trip_json())
            else:
                self._send(404, '{"ok":false}')

        def do_POST(self):
            if not self._guard():
                return
            if "application/json" not in (self.headers.get("Content-Type") or ""):
                self._send(415, '{"ok":false,"msg":"json only"}')
                return
            b = self._body()
            try:
                if self.path == "/api/cmd":
                    r = adb.send(b.get("cmd"), **(b.get("args") or {}))
                    self._send(200, json.dumps(r, ensure_ascii=False))
                elif self.path == "/api/trip":
                    t = state["trip"]
                    if b.get("action") == "start" and not (t and t.running):
                        state["trip"] = Trip(adb)
                        state["trip"].start()
                    elif b.get("action") == "stop" and t:
                        t.stop_flag = True
                    self._send(200, self._trip_json())
                else:
                    self._send(404, '{"ok":false}')
            except Exception as e:   # adb หาย / timeout → ส่งข้อความให้หน้าเว็บแสดง
                self._send(200, json.dumps({"ok": False, "msg": str(e)}, ensure_ascii=False))

    return H


def serve(adb, port):
    srv = ThreadingHTTPServer(("127.0.0.1", port), make_handler(adb, port))
    print("หน้าควบคุมรถจำลอง: http://127.0.0.1:%d  (Ctrl+C เพื่อหยุด)" % port)
    print("adb:", adb.adb or "ไม่พบ")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


def main(argv=None):
    for st in (sys.stdout, sys.stderr):   # คอนโซล Windows ไม่ใช่ UTF-8 → ไทยเพี้ยน
        try:
            st.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    ap = argparse.ArgumentParser(prog="carsim.py", description=__doc__.split("\n")[0],
                                 formatter_class=argparse.RawDescriptionHelpFormatter, epilog=__doc__.split("\n", 1)[1])
    ap.add_argument("--adb", help="path ของ adb (ไม่ใส่ = หาใน PATH แล้ว %%LOCALAPPDATA%%\\Android\\Sdk\\platform-tools)")
    ap.add_argument("-s", "--serial", help="serial เครื่อง (ไม่ใส่ = emulator-* ตัวแรก)")
    sub = ap.add_subparsers(dest="cmd")
    sp = sub.add_parser("serve", help="เปิดหน้าควบคุมบนเว็บ (ค่าเริ่ม)")
    sp.add_argument("--port", type=int, default=8765)
    sub.add_parser("dump", help="อ่านสถานะ (JSON)")
    sub.add_parser("reset", help="รีเซ็ตรถจำลอง")
    for name, h in (("speed", "เป้าความเร็ว km/h"), ("gear", "เกียร์ P/R/N/D"), ("plug", "เสียบสายชาร์จ 1/0"), ("soc", "ตั้งแบต (เปอร์เซ็นต์)"),
                    ("chargekw", "กำลังชาร์จ kW"), ("wash", "สถานะโหมดล้างรถ active/notavailable"), ("off", "ปิด/เปิดรถจำลอง 1/0")):
        p = sub.add_parser(name, help=h)
        p.add_argument("v")
    p = sub.add_parser("key", help="กดปุ่มพวงมาลัย: left right up down voice หรือรหัส 0x4102xxxx")
    p.add_argument("v")
    p.add_argument("--long", action="store_true", help="กดค้าง 1 วิ")
    p = sub.add_parser("set", help="ตั้ง property ตรง ๆ: set PROP AREA VALUE [--type i|f|b]")
    p.add_argument("prop"); p.add_argument("area"); p.add_argument("v")
    p.add_argument("--type", choices=["i", "f", "b"])
    p = sub.add_parser("trip", help="ทริปทดสอบ: D, เร่ง 60, ล่อง, หยุด, P")
    p.add_argument("--cruise", type=int, default=120, help="วินาทีที่ล่อง (ค่าเริ่ม 120)")
    p.add_argument("--kmh", type=int, default=60)
    a = ap.parse_args(argv)
    adb = Adb(a.adb, a.serial)

    if a.cmd in (None, "serve"):
        serve(adb, getattr(a, "port", 8765))
        return 0
    try:
        if a.cmd == "trip":
            t = Trip(adb, a.cruise, a.kmh)
            t.start()
            last = None
            while t.is_alive():
                if t.step != last:
                    last = t.step
                    print(last, flush=True)
                time.sleep(0.5)
            print(t.note)
            return 0
        if a.cmd == "key":
            r = adb.send("key", v=KEYS.get(a.v, a.v), long="1" if a.long else None)
        elif a.cmd == "set":
            r = adb.send("set", prop=a.prop, area=a.area, v=a.v, type=a.type)
        elif a.cmd in ("dump", "reset"):
            r = adb.send(a.cmd)
        else:
            r = adb.send(a.cmd, v=a.v)
    except Exception as e:
        print("ผิดพลาด:", e, file=sys.stderr)
        return 2
    print(json.dumps(r, ensure_ascii=False, indent=2 if a.cmd == "dump" else None))
    return 0 if r.get("ok") else 1


if __name__ == "__main__":
    sys.exit(main())
