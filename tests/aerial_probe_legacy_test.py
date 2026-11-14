"""Real AdbClient socket -> legacy shell wrapper -> restricted shell functions, no television."""
import socket,struct,subprocess,tempfile,threading
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def exact(s,n):
 data=b''
 while len(data)<n:
  part=s.recv(n-len(data))
  if not part:raise EOFError()
  data+=part
 return data
def receive(s):
 cmd,a,b,n,checksum,magic=struct.unpack('<6I',exact(s,24));return cmd,a,b,exact(s,n)
def send(s,cmd,a=0,b=0,data=b''):
 c=int.from_bytes(cmd.encode(),'little');s.sendall(struct.pack('<6I',c,a,b,len(data),sum(data),c^0xffffffff)+data)
functions=r'''printf() { return 127; }
getprop() {
 if [ "$#" -ne 0 ]; then return 1; fi
 echo '[ro.product.model]: [8H66 H7]'
 echo '[ro.build.version.sdk]: [22]'
 echo '[ro.build.fingerprint]: [qa/vendor/android5]'
 echo '[ro.product.cpu.abi]: [arm64-v8a]'
 echo '[ro.product.cpu.abi2]: [armeabi-v7a]'
}
pm() {
 case "$1 $2" in
  'path app.orchard.tv') return 1;;
  'path app.orchard.tv.lite') echo 'package:/data/app/lite/base.apk';;
  'list packages') echo 'package:android'; echo 'package:app.orchard.tv.lite';;
  *) return 2;;
 esac
}
dumpsys() { echo 'versionCode=12 minSdk=14'; echo 'versionName=1.11'; }
denied_query() { echo 'Permission denied' >&2; return 1; }
'''
errors=[]
def peer(listener):
 try:
  with listener:
   s,_=listener.accept()
   with s:
    s.settimeout(20);receive(s);send(s,'CNXN',0x01000000,4096,b'device::\0')
    while True:
     try:cmd,a,b,data=receive(s)
     except EOFError:break
     if cmd==int.from_bytes(b'OPEN','little'):
      service=data.rstrip(b'\0').decode();assert service.startswith('shell:')
      result=subprocess.run(['/bin/sh','-c',functions+service[6:]],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,env={'PATH':'/nonexistent'},timeout=3)
      output=result.stdout.replace(b'\n',b'\r\r\n');send(s,'OKAY',a+100,a)
      for at in range(0,len(output),19):send(s,'WRTE',a+100,a,output[at:at+19])
      send(s,'CLSE',a+100,a)
 except BaseException as e:errors.append(e)
with tempfile.TemporaryDirectory() as keys:
 listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen();port=listener.getsockname()[1]
 thread=threading.Thread(target=peer,args=(listener,),daemon=True);thread.start()
 r=subprocess.run(['java','-cp',str(ROOT/'build/test-classes'),'cn.linkdeck.adb.AerialProbeLegacyChecks',str(port),keys],capture_output=True,text=True,timeout=35)
 thread.join(3);print(r.stdout,end='')
 if r.returncode or errors:raise RuntimeError((r.stderr,errors))
