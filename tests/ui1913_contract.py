"""Exact 0.19.13 scope guard: local stable-version feature removed (AerialProfiles deleted);
Aerial center merges into 4 entries (check update / version management / desktop assistant /
fault report); MainActivity joins the allowed set, everything else must match the 0.19.7 baseline."""
from pathlib import Path
import json,hashlib,subprocess,re
r=Path(__file__).resolve().parents[1]
changed={'AppVersion.java','DeckDialogBuilder.java','AuroraStyle.java','AuroraLayout.java','SoftBackdrop.java','GlassSurface.java','DepthSurface.java','RemoteFace.java','RemoteKeyArt.java','VolumeGlide.java','MainActivity.java'}
def digest(data):return hashlib.sha256(data).hexdigest()
def snapshot(root):
 src=root/'app/src/main/java/cn/linkdeck/adb'
 files={str(p.relative_to(root)):digest(p.read_bytes()) for p in src.glob('*.java') if p.name not in changed and p.name not in {'DialogListTint.java','AuroraGlow.java'}}
 for p in (root/'app/src/main/res').rglob('*'):
  if p.is_file():files[str(p.relative_to(root))]=digest(p.read_bytes())
 files['app/src/main/AndroidManifest.xml']=digest((root/'app/src/main/AndroidManifest.xml').read_bytes())
 v=(src/'VolumeGlide.java').read_text();files['volume_events']=digest(v[v.index(' public boolean onTouchEvent'):].encode())
 return files
if __name__=='__main__':
 expected=json.loads((r/'tests/ui198_exact_baseline.json').read_text());expected.pop('app/src/main/java/cn/linkdeck/adb/MainActivity.java',None);expected.pop('app/src/main/java/cn/linkdeck/adb/AerialProfiles.java',None);actual=snapshot(r)
 assert expected==actual,'0.19.13 activity/layout/resources/business scope changed'
 main=(r/'app/src/main/java/cn/linkdeck/adb/MainActivity.java').read_text()
 for gone in ['本机稳定版本','稳定版本管理','暂缓切换','AerialProfiles','stableManager','showHeldVersion','aerialOptions','lastFaultReport','evaluateHeld','overrideHold','profileText','"一键故障采集",()->captureDesktopFault()','"上次故障报告",()->lastFaultReport()','收起版本工具','版本管理与故障采集','版本选择与支持说明']:
  assert gone not in main,f'0.19.13 removed string still present: {gone}'
 for want in ['pair(c,"检查更新",()->checkAerial("auto"),"桌面助手",()->checkDesktop());','pair(c,"版本管理",()->versionManager(),"故障报告",()->faultReport());','private void versionManager()','private void faultReport()','addButton(list,"故障报告",false,()->faultReport());']:
  assert want in main,f'0.19.13 merged entry missing: {want}'
 print(f'UI 0.19.13 SCOPE PASSED: {len(actual)} exact baseline entries; XML, permissions, event logic unchanged (MainActivity is in the allowed set for this release)')
 subprocess.run(['python3',str(r/'tests/dialog_tint_test.py')],check=True)
