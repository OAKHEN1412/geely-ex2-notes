# -*- coding: utf-8 -*-
# push_plugin.py — client ฝั่งคอม คุยกับ dev harness (DevService) ใน EX2 BY OAKHEN ผ่าน WiFi
# ping:  py push_plugin.py <IP> ping
# push:  py push_plugin.py <IP> push <ClassName> <plugin.dex> [arg]
#   เช่น  py push_plugin.py 172.20.10.3 push com.oakhen.plugins.HelloPlugin out/hello.dex "sawadee"
import sys, socket, struct

PORT = 47811
TOKEN = b"OAKHEN"

def wstr(s):
    b = s.encode("utf-8"); return struct.pack(">i", len(b)) + b

def send(ip, payload):
    s = socket.create_connection((ip, PORT), timeout=120)
    s.sendall(b"EX2D" + wstr(TOKEN.decode()) + payload)
    # อ่าน reply: int len + utf8
    hdr = b""
    while len(hdr) < 4:
        c = s.recv(4 - len(hdr))
        if not c: break
        hdr += c
    if len(hdr) < 4:
        s.close(); return "(no reply)"
    n = struct.unpack(">i", hdr)[0]
    data = b""
    while len(data) < n:
        c = s.recv(n - len(data))
        if not c: break
        data += c
    s.close()
    return data.decode("utf-8", "replace")

def main():
    ip = sys.argv[1]; cmd = sys.argv[2]
    if cmd == "ping":
        print(send(ip, b"R"))
    elif cmd == "wake":
        print(send(ip, b"W"))
    elif cmd == "push":
        cls = sys.argv[3]; dexpath = sys.argv[4]; arg = sys.argv[5] if len(sys.argv) > 5 else ""
        dex = open(dexpath, "rb").read()
        payload = b"P" + wstr(cls) + wstr(arg) + struct.pack(">q", len(dex)) + dex
        print(send(ip, payload))
    else:
        print("cmd: ping | push <Class> <dex> [arg]"); sys.exit(1)

if __name__ == "__main__":
    main()
