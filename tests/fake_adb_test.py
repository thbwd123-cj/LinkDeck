"""Independent ADB peer exercising real sockets and cryptographic authentication.
Not a replacement for Android or television testing.
Run after compiling ClientIntegration and core classes to build/test-classes.
"""
import base64, hashlib, os, re, socket, struct, subprocess, tempfile, threading
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def code(s):return int.from_bytes(s.encode(),'little')
def exact(s,n):
    b=b''
    while len(b)<n:
        p=s.recv(n-len(b))
        if not p:raise EOFError()
        b+=p
    return b
def receive(s):
    cmd,a,b,n,checksum,magic=struct.unpack('<6I',exact(s,24));data=exact(s,n)
    assert checksum==sum(data)&0xffffffff and magic==(cmd^0xffffffff)
    return cmd,a,b,data
def send(s,cmd,a=0,b=0,data=b'',bad=False):
    c=code(cmd);s.sendall(struct.pack('<6I',c,a,b,len(data),(sum(data)+(1 if bad else 0))&0xffffffff,c^0xffffffff)+data)
def sync(cmd,data=b''):return cmd.encode()+struct.pack('<I',len(data))+data

def peer(listener,bad,errors):
    try:
        s,_=listener.accept();s.settimeout(15)
        with s:
            assert receive(s)[0]==code('CNXN');token=hashlib.sha1(b'linkdeck-independent-test').digest();send(s,'AUTH',1,0,token)
            cmd,kind,_,sig=receive(s);assert cmd==code('AUTH') and kind==2
            send(s,'AUTH',1,0,token);cmd,kind,_,pub=receive(s);assert cmd==code('AUTH') and kind==3
            key=base64.b64decode(pub.split(b' ')[0]);words,n0=struct.unpack('<II',key[:8]);n=int.from_bytes(key[8:264],'little');rr=int.from_bytes(key[264:520],'little');exp=struct.unpack('<I',key[520:])[0]
            assert words==64 and exp==65537 and rr==pow(2,4096,n) and (n0*n+1)%(1<<32)==0
            recovered=pow(int.from_bytes(sig,'big'),exp,n).to_bytes(256,'big')
            assert recovered==b'\0\1'+b'\xff'*218+b'\0'+bytes.fromhex('3021300906052b0e03021a05000414')+token
            send(s,'CNXN',0x1000000,4096,b'device::\0',bad)
            if bad:return
            channels={};files={};expected=bytes(i%251 for i in range(150123))
            def respond(cid,data):send(s,'WRTE',cid+100,cid,data)
            def end(cid):send(s,'CLSE',cid+100,cid)
            while True:
                try:cmd,a,b,data=receive(s)
                except EOFError:break
                if cmd==code('OPEN'):
                    service=data.rstrip(b'\0').decode();send(s,'OKAY',a+100,a)
                    if service.startswith('shell:'):
                        tag=re.search(r'__LINKDECK_[0-9a-f]+__',service)[0]
                        body='hello' if 'echo hello' in service else 'alpha' if 'echo alpha' in service else 'beta' if 'echo beta' in service else 'failure'
                        status=7 if '( false\n' in service else 0
                        respond(a,(body+'\r\n'+tag+str(status)+'\r\n').encode());end(a)
                    elif service.startswith('exec:'):respond(a,b'\x89PNG\r\n\x1a\nDATA');end(a)
                    elif service=='sync:':channels[a]={'buf':b'','data':bytearray(),'path':None}
                    else:raise AssertionError(service)
                elif cmd==code('WRTE'):
                    cid=a;send(s,'OKAY',cid+100,cid);ch=channels[cid];ch['buf']+=data
                    while len(ch['buf'])>=8:
                        sid=ch['buf'][:4];n=struct.unpack('<I',ch['buf'][4:8])[0]
                        if sid==b'DONE':ch['buf']=ch['buf'][8:];assert bytes(ch['data'])==expected;files[ch['path']]=bytes(ch['data']);respond(cid,b'OKAY'+struct.pack('<I',0));continue
                        if len(ch['buf'])<8+n:break
                        payload=ch['buf'][8:8+n];ch['buf']=ch['buf'][8+n:]
                        if sid==b'SEND':ch['path']=payload.decode().rsplit(',',1)[0]
                        elif sid==b'DATA':ch['data'].extend(payload)
                        elif sid==b'LIST':
                            items=[('测试 file.bin',0o100644,len(expected)),('folder',0o040755,0)]
                            packet=b''
                            for name,mode,size in items:
                                raw=name.encode();packet+=b'DENT'+struct.pack('<4I',mode,size,100,len(raw))+raw
                            packet+=b'DONE'+b'\0'*16
                            # Fragment both sync headers and data across ADB packets.
                            for at in range(0,len(packet),7):respond(cid,packet[at:at+7])
                        elif sid==b'RECV':
                            path=payload.decode()
                            if path=='/protected':respond(cid,sync('FAIL',b'permission denied'));continue
                            content=files[path]
                            for at in range(0,len(content),8000):respond(cid,sync('DATA',content[at:at+8000]))
                            respond(cid,b'DONE'+b'\0'*4)
                        else:raise AssertionError(sid)
                elif cmd==code('CLSE'):channels.pop(a,None)
                elif cmd==code('OKAY'):pass
                else:raise AssertionError(cmd)
    except BaseException as e:errors.append(e)

with tempfile.TemporaryDirectory() as keydir:
    for bad in [False,False,True]:
        with socket.socket() as listener:
            listener.bind(('127.0.0.1',0));listener.listen();errors=[];thread=threading.Thread(target=peer,args=(listener,bad,errors),daemon=True);thread.start()
            args=['/usr/bin/java','-cp',str(ROOT/'build/test-classes'),'cn.linkdeck.adb.ClientIntegration',str(listener.getsockname()[1]),keydir]
            if bad:args.append('bad')
            result=subprocess.run(args,text=True,capture_output=True,timeout=40);thread.join(5)
            print(result.stdout,end='')
            if result.returncode or errors:raise RuntimeError((result.stderr,errors))
print('ALL PROTOCOL TESTS PASSED (including reconnect with persisted keys).')
