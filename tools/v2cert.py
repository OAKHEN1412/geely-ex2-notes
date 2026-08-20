# -*- coding: utf-8 -*-
# ดึง X.509 cert จาก APK Signing Block v2/v3 (ไม่ต้องใช้ Java) แล้วพิมพ์ subject + SHA-256
import sys, struct, hashlib, subprocess, os

def find_eocd(d):
    # End Of Central Directory
    i = d.rfind(b'PK\x05\x06')
    if i < 0: raise ValueError('no EOCD')
    cd_off = struct.unpack_from('<I', d, i+16)[0]
    return cd_off

def get_sig_block(d):
    cd = find_eocd(d)
    magic = b'APK Sig Block 42'
    if d[cd-16:cd] != magic: raise ValueError('no APK Sig Block magic')
    size2 = struct.unpack_from('<Q', d, cd-24)[0]
    start = cd - 8 - size2
    blk = d[start:cd-16]  # id-value pairs region (between the two size fields)
    # blk begins right after the leading size8; walk pairs
    return d[start+8:cd-16]

def iter_pairs(blk):
    off = 0
    while off + 8 <= len(blk):
        ln = struct.unpack_from('<Q', blk, off)[0]; off += 8
        if ln == 0 or off+ln > len(blk): break
        bid = struct.unpack_from('<I', blk, off)[0]
        yield bid, blk[off+4:off+ln]
        off += ln

def certs_from_scheme(val):
    # val = length-prefixed 'signers'
    out=[]
    def u32(b,o): return struct.unpack_from('<I', b, o)[0]
    try:
        o=0; signers_len=u32(val,o); o+=4
        signers=val[o:o+signers_len]; so=0
        while so+4<=len(signers):
            slen=u32(signers,so); so+=4
            signer=signers[so:so+slen]; so+=slen
            # signed data (length-prefixed)
            sd_len=u32(signer,0); sd=signer[4:4+sd_len]
            # signed data: digests(lp), certificates(lp), ...
            do=0; dg_len=u32(sd,do); do+=4+dg_len
            certs_len=u32(sd,do); do+=4
            certs=sd[do:do+certs_len]; co=0
            while co+4<=len(certs):
                clen=u32(certs,co); co+=4
                out.append(certs[co:co+clen]); co+=clen
    except Exception as e:
        pass
    return out

def dump(path,label):
    d=open(path,'rb').read()
    blk=get_sig_block(d)
    ids={bid:val for bid,val in iter_pairs(blk)}
    certs=[]
    for scheme in (0x7109871a, 0xf05368c0):  # v2, v3
        if scheme in ids:
            certs=certs_from_scheme(ids[scheme])
            if certs: break
    print('== %s =='%label)
    if not certs:
        print('  (no v2/v3 cert found; ids=%s)'%[hex(x) for x in ids]); return None
    der=certs[0]
    fp=hashlib.sha256(der).hexdigest().upper()
    fp=':'.join(fp[i:i+2] for i in range(0,len(fp),2))
    # subject via openssl
    try:
        p=subprocess.run(['openssl','x509','-inform','DER','-noout','-subject','-issuer'],
                         input=der, capture_output=True, timeout=15)
        subj=p.stdout.decode(errors='replace').strip()
    except Exception as e:
        subj='(openssl failed: %s)'%e
    print('  cert SHA-256:', fp)
    print(' ',subj.replace('\n','\n  '))
    return fp

f1=dump(sys.argv[1],'DEVICE platform key (framework-res.apk)')
f2=dump(sys.argv[2],'OUR APK (Geely-Ex2-Car.apk)')
print()
print('MATCH (device==apk)?', f1==f2 and f1 is not None)
