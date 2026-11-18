"""Exact 0.19.14 scope guard: Halo TV desktop management added (HaloSupport/HaloDownload/
HaloInstaller/HaloProbe are new); DesktopAssistant parameterized by desktop Profile with a
switch-desktop operation; MainActivity gains the Halo flow and a parameterized desktop flow;
OfficialAppSources gains the HALO type. Everything else must match the 0.19.7 baseline."""
from pathlib import Path
import json,hashlib,subprocess,re
r=Path(__file__).resolve().parents[1]
changed={'AppVersion.java','DeckDialogBuilder.java','AuroraStyle.java','AuroraLayout.java','SoftBackdrop.java','GlassSurface.java','DepthSurface.java','RemoteFace.java','RemoteKeyArt.java','VolumeGlide.java','MainActivity.java','DesktopAssistant.java','DesktopDiagnostic.java','OfficialAppSources.java'}
new={'HaloSupport.java','HaloDownload.java','HaloInstaller.java','HaloProbe.java'}
def digest(data):return hashlib.sha256(data).hexdigest()
def snapshot(root):
 src=root/'app/src/main/java/cn/linkdeck/adb'
 files={str(p.relative_to(root)):digest(p.read_bytes()) for p in src.glob('*.java') if p.name not in changed and p.name not in new and p.name not in {'DialogListTint.java','AuroraGlow.java'}}
 for p in (root/'app/src/main/res').rglob('*'):
  if p.is_file():files[str(p.relative_to(root))]=digest(p.read_bytes())
 files['app/src/main/AndroidManifest.xml']=digest((root/'app/src/main/AndroidManifest.xml').read_bytes())
 v=(src/'VolumeGlide.java').read_text();files['volume_events']=digest(v[v.index(' public boolean onTouchEvent'):].encode())
 return files
if __name__=='__main__':
 expected=json.loads((r/'tests/ui198_exact_baseline.json').read_text())
 for popped in ['app/src/main/java/cn/linkdeck/adb/MainActivity.java','app/src/main/java/cn/linkdeck/adb/AerialProfiles.java','app/src/main/java/cn/linkdeck/adb/AerialVersionChecks.java','app/src/main/java/cn/linkdeck/adb/DesktopAssistant.java','app/src/main/java/cn/linkdeck/adb/DesktopDiagnostic.java','app/src/main/java/cn/linkdeck/adb/OfficialAppSources.java']:
  expected.pop(popped,None)
 actual=snapshot(r)
 assert expected==actual,'0.19.14 activity/layout/resources/business scope changed'
 main=(r/'app/src/main/java/cn/linkdeck/adb/MainActivity.java').read_text()
 for gone in ['desktopSnapshot','desktopClient','desktopEndpoint','desktopManual','desktopOutcome','desktopDialog','showDesktopPanel()','faultReport()','captureDesktopFault()','browseAerialReleases','DesktopAssistant.inspect(d,e)','DesktopAssistant.permission(dump)']:
  assert gone not in main,f'0.19.14 removed string still present: {gone}'
 for want in ['Halo TV 桌面管理','haloCenter()','checkHalo("auto")','app.halotv.launcher','switchDesktop','DesktopAssistant.HALO','DesktopAssistant.AERIAL','HaloDownload','HaloSupport','HaloInstaller','HaloProbe','pair(c,"检查更新",()->checkAerial("auto"),"桌面助手",()->checkDesktop());','pair(c,"版本管理",()->versionManager(),"故障报告",()->faultReport(DesktopAssistant.AERIAL,aerialDesktop));','切换到 ']:
  assert want in main,f'0.19.14 Halo/switch entry missing: {want}'
 print(f'UI 0.19.14 SCOPE PASSED: {len(actual)} exact baseline entries; XML, permissions, event logic unchanged (MainActivity, DesktopAssistant, DesktopDiagnostic, OfficialAppSources are in the allowed set for this release)')
 subprocess.run(['python3',str(r/'tests/dialog_tint_test.py')],check=True)
