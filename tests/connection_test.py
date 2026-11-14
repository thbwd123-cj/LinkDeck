"""Synthetic TCP peers verify connection failure classification and cancellation."""
import socket, struct, subprocess, tempfile, threading, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def send(s,cmd,a=0,data=b''):
 c=int.from_bytes(cmd.encode(),'little');s.sendall(struct.pack('<6I',c,a,4096,len(data),sum(data),c^0xffffffff)+data)
with tempfile.TemporaryDirectory() as keys:
 for mode in ['refused','eof','protocol','tls','timeout','cancel']:
  listener=socket.socket();listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
  errors=[]
  def peer():
   try:
    with listener:
     s,_=listener.accept()
     with s:
      s.recv(4096)
      if mode=='eof':return
      if mode=='protocol':send(s,'FAIL')
      elif mode=='tls':send(s,'STLS')
      else:send(s,'AUTH',1,b'a'*20)
      time.sleep(.7)
   except Exception as e:errors.append(e)
  if mode=='refused':listener.close();thread=None
  else:listener.listen();thread=threading.Thread(target=peer);thread.start()
  r=subprocess.run(['java','-cp',str(ROOT/'build/test-classes'),'cn.linkdeck.adb.ConnectionChecks',str(port),keys,mode],text=True,capture_output=True,timeout=8)
  if thread:thread.join(3)
  print(r.stdout,end='')
  if r.returncode or errors:raise RuntimeError((r.stderr,errors))
print('ALL CONNECTION FAILURE CHECKS PASSED')
