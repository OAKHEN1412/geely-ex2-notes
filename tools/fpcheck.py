# -*- coding: utf-8 -*-
# ตรวจ cert fingerprint (SHA-256) ของ APK (v2/v3 signing block) เทียบกับค่าที่คาดหวัง
import sys, struct, hashlib, subprocess

def get_sig_block(d):
    i = d.rfind(b'PK\x05\x06'); cd = struct.unpack_from('<I', d, i+16)[0]
    if d[cd-16:cd] != b'APK Sig Block 42': raise ValueError('no v2/v3 sig block')
    size2 = struct.unpack_from('<Q', d, cd-24)[0]; start = cd-8-size2
    return d[start+8:cd-16]

def pairs(blk):
    o=0; out={}
    while o+8<=len(blk):
        ln=struct.unpack_from('<Q',blk,o)[0]; o+=8
        if ln==0 or o+ln>len(blk): break
        out[struct.unpack_from('<I',blk,o)[0]]=blk[o+4:o+ln]; o+=ln
    return out

def first_cert(val):
    u=lambda b,o: struct.unpack_from('<I',b,o)[0]
    o=0; sl=u(val,o); o+=4; signers=val[o:o+sl]
    slen=u(signers,0); signer=signers[4:4+slen]
    sd_len=u(signer,0); sd=signer[4:4+sd_len]
    do=0; dg=u(sd,do); do+=4+dg; cl=u(sd,do); do+=4; certs=sd[do:do+cl]
    clen=u(certs,0); return certs[4:4+clen]

apk=sys.argv[1]; expect=sys.argv[2].upper()
d=open(apk,'rb').read()
ids=pairs(get_sig_block(d))
val=ids.get(0x7109871a) or ids.get(0xf05368c0)
der=first_cert(val)
fp=hashlib.sha256(der).hexdigest().upper()
fp=':'.join(fp[i:i+2] for i in range(0,len(fp),2))
subj=subprocess.run(['openssl','x509','-inform','DER','-noout','-subject'],
                    input=der,capture_output=True).stdout.decode('replace').strip()
print('    signed APK cert :', fp)
print('    expected(device):', expect)
print('   ', subj)
print('    ==> MATCH' if fp==expect else '    ==> !!! MISMATCH')
sys.exit(0 if fp==expect else 2)
