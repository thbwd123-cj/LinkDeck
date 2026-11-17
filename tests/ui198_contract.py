"""Scope guard against the 0.19.7 baseline; excludes only reviewed visual methods."""
from pathlib import Path
import re,json,hashlib,subprocess
r=Path(__file__).resolve().parents[1];src=r/'app/src/main/java/cn/linkdeck/adb'
VISUAL={'card','button','show','appearance','settingsMenu','home','remote','tools'}
DRAWING={'MainActivity.java','AppVersion.java','SoftBackdrop.java','DepthSurface.java','RemoteFace.java','RemoteKeyArt.java','VolumeGlide.java','DeckDialogBuilder.java','DialogListTint.java','AuroraGlow.java'}
def sha(s):return hashlib.sha256(s.encode() if isinstance(s,str) else s).hexdigest()
def balanced(s,start,left='{',right='}'):
 depth=0;i=start;quote=None
 while i<len(s):
  c=s[i]
  if quote:
   if c=='\\':i+=2;continue
   if c==quote:quote=None
  elif s.startswith('//',i):
   n=s.find('\n',i);i=len(s) if n<0 else n;continue
  elif s.startswith('/*',i):i=s.index('*/',i+2)+2;continue
  elif c in '\"\'':quote=c
  elif c==left:depth+=1
  elif c==right:
   depth-=1
   if depth==0:return i+1
  i+=1
 raise AssertionError('Unbalanced Java')
def methods(s):
 out={}
 for m in re.finditer(r'^  (?:private|public|protected)\s+(?:(?:static|final|synchronized)\s+)*[\w<>.\[\]]+\s+(\w+)\s*\(',s,re.M):
  start=s.index('{',m.end());signature=s[m.start():start].strip()
  out[signature]=(m.group(1),s[m.start():balanced(s,start)])
 return out
def calls(s,name):
 out=[]
 for m in re.finditer(r'\.'+name+r'\(',s):
  start=m.end()-1;out.append(s[m.start():balanced(s,start,'(',')')])
 return out
def snapshot(root):
 p=root/'app/src/main/java/cn/linkdeck/adb';main=(p/'MainActivity.java').read_text();volume=(p/'VolumeGlide.java').read_text().replace('private Shader knob,glow,glowLine;','private Shader knob,glow;')
 return {'business_files':{f.name:sha(f.read_bytes()) for f in p.glob('*.java') if f.name not in DRAWING and f.name not in {'AuroraStyle.java','AuroraBlur.java','AuroraLayout.java','GlassSurface.java','ConnectionLamp.java'}},
 'method_signatures':sorted(methods(main)),
 'business_methods':{k:sha(v) for k,(name,v) in methods(main).items() if name not in VISUAL},
 'click_callbacks':sha('\n'.join(calls(main,'setOnClickListener'))),
 'touch_callbacks':sha('\n'.join(calls(main,'setOnTouchListener'))),
 'accessibility_names':sha('\n'.join(calls(main,'setContentDescription'))),
 'volume_events':sha(volume[volume.index(' public boolean onTouchEvent'):]),
 'volume_repeat_constructor':sha(volume[:volume.index(' protected void onSizeChanged')]),
 'manifest':sha((root/'app/src/main/AndroidManifest.xml').read_bytes())}
def check():
 baseline=json.loads((r/'tests/ui197_contract.json').read_text());current=snapshot(r)
 baseline['business_files'].pop('DeckDialogBuilder.java',None)
 for key in baseline:assert baseline[key]==current[key],f'Functional preservation check failed: {key}'
 for name in ['AuroraStyle','AuroraBlur','AuroraLayout','GlassSurface','ConnectionLamp','SoftBackdrop','DepthSurface','RemoteFace','RemoteKeyArt']:
  text=(src/(name+'.java')).read_text()
  for forbidden in ['.shell(','.exec(','.connect(','.putString(','.putBoolean(','onTouchEvent(','performAccessibilityAction(']:assert forbidden not in text,(name,forbidden)
 print(f'UI SCOPE PASSED: {len(current["business_files"])} unchanged classes, {len(current["business_methods"])} unchanged methods, click/touch callbacks, accessibility names, volume events and manifest identical to 0.19.7')
 out=r/'build/aurora-test';out.mkdir(parents=True,exist_ok=True)
 subprocess.run(['java','-jar',str(r/'.toolchain/ecj.jar'),'-8','-nowarn','-encoding','UTF-8','-d',str(out),*[str(src/(n+'.java')) for n in ['UiPalette','AuroraStyle','AuroraBlur']],str(r/'tests/AuroraChecks.java')],check=True)
 subprocess.run(['java','-cp',str(out),'cn.linkdeck.adb.AuroraChecks'],check=True)
if __name__=='__main__':check()
