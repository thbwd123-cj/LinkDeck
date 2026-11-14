"""Real socket ADB peer: stalled commands and continuous output, no television access."""
import socket,struct,subprocess,tempfile,threading,time,re
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def exact(s,n):
 b=b''
 while len(b)<n:
  p=s.recv(n-len(b))
  if not p:raise EOFError()
  b+=p
 return b
def recv(s):
 c,a,b,n,_,_=struct.unpack('<6I',exact(s,24));return c,a,b,exact(s,n)
def code(s):return int.from_bytes(s.encode(),'little')
for mode in ['partial','silent','stream','ack']:
 errors=[];lock=threading.Lock();stop=threading.Event()
 def peer(listener):
  def send(s,c,a=0,b=0,data=b''):
   c=code(c)
   with lock:s.sendall(struct.pack('<6I',c,a,b,len(data),sum(data),c^0xffffffff)+data)
  try:
   with listener:
    s,_=listener.accept()
    with s:
     s.settimeout(6);recv(s);send(s,'CNXN',0x1000000,4096,b'device::\0')
     while True:
      try:c,a,b,data=recv(s)
      except EOFError:break
      if c!=code('OPEN'):continue
      text=data.decode();tag=re.search(r'__LINKDECK_[0-9a-f]+__',text)[0]
      if 'echo recovered' in text:
       send(s,'OKAY',a+100,a);send(s,'WRTE',a+100,a,('recovered\n'+tag+'0\n').encode());send(s,'CLSE',a+100,a);continue
      if mode=='ack':continue
      send(s,'OKAY',a+100,a)
      if mode=='partial':send(s,'WRTE',a+100,a,'  已返回部分文本  \r\n'.encode())
      if mode=='stream':
       def stream(cid):
        try:
         for _ in range(100):
          if stop.wait(.025):break
          send(s,'WRTE',cid+100,cid,b'progress\n')
        except OSError:pass
       threading.Thread(target=stream,args=(a,),daemon=True).start()
  except BaseException as e:errors.append(e)
 with tempfile.TemporaryDirectory() as keys:
  listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen();port=listener.getsockname()[1]
  t=threading.Thread(target=peer,args=(listener,),daemon=True);t.start()
  r=subprocess.run(['java','-cp',str(R/'build/test-classes'),'cn.linkdeck.adb.ShellTimeoutChecks',str(port),keys,mode],capture_output=True,text=True,timeout=8)
  stop.set();t.join(2);print(r.stdout,end='')
  if r.returncode or errors:raise RuntimeError((r.stderr,errors))
print('SHELL TIMEOUT CHECKS PASSED: 4 scenarios')
