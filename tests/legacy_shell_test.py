"""Executes received ADB shell commands using a real restricted shell, not canned markers.
Simulates an older/vendor image where printf is unavailable. Not a television test.
"""
import socket,struct,subprocess,tempfile,threading,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def exact(s,n):
 b=b''
 while len(b)<n:
  p=s.recv(n-len(b))
  if not p:raise EOFError()
  b+=p
 return b
def receive(s):
 cmd,a,b,n,checksum,magic=struct.unpack('<6I',exact(s,24));return cmd,a,b,exact(s,n)
def send(s,cmd,a=0,b=0,data=b''):
 c=int.from_bytes(cmd.encode(),'little');s.sendall(struct.pack('<6I',c,a,b,len(data),sum(data),c^0xffffffff)+data)
prefix="printf() { echo 'printf: not found' >&2; return 127; }; "
old=subprocess.run(['/bin/sh','-c',prefix+"( echo hello\n); __ld_status=$?; printf '\\n__OLD__%s\\n' \"$__ld_status\""],capture_output=True)
assert b'__OLD__' not in old.stdout and b'printf: not found' in old.stderr
errors=[]
def peer(listener):
 try:
  with listener:
   s,_=listener.accept()
   with s:
    s.settimeout(15);receive(s);send(s,'CNXN',0x01000000,4096,b'device::\0')
    while True:
     try:cmd,a,b,data=receive(s)
     except EOFError:break
     if cmd==int.from_bytes(b'OPEN','little'):
      service=data.rstrip(b'\0').decode();assert service.startswith('shell:');script=service[6:]
      result=subprocess.run(['/bin/sh','-c',prefix+script],capture_output=True,env={'PATH':'/nonexistent'},timeout=3)
      output=result.stdout+result.stderr
      if '( echo MISSING\n' in script:output=b'MISSING\n'
      elif '( echo MALFORMED\n' in script:
       tag=re.search(r'__LINKDECK_[0-9a-f]+__',script)[0];output=('MALFORMED\n'+tag+'garbage\n').encode()
      # PTY CRCRLF and fragmentation also occur on legacy transports.
      output=output.replace(b'\n',b'\r\r\n');send(s,'OKAY',a+100,a)
      for i in range(0,len(output),11):send(s,'WRTE',a+100,a,output[i:i+11])
      send(s,'CLSE',a+100,a)
 except BaseException as e:errors.append(e)
with tempfile.TemporaryDirectory() as keys:
 listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen();port=listener.getsockname()[1]
 thread=threading.Thread(target=peer,args=(listener,),daemon=True);thread.start()
 r=subprocess.run(['java','-cp',str(ROOT/'build/test-classes'),'cn.linkdeck.adb.LegacyShellChecks',str(port),keys],capture_output=True,text=True,timeout=30)
 thread.join(3);print(r.stdout,end='')
 if r.returncode or errors:raise RuntimeError((r.stderr,errors))
print('OLD printf WRAPPER REPRODUCED FAILURE; NEW LEGACY SHELL CHECKS PASSED')
