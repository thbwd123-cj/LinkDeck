"""Exact 0.19.21 scope guard: the background service survives the app being
swiped away, heartbeats every 60 s, reconnects by itself, restarts sticky, and
explains a missing notification (POST_NOTIFICATIONS) instead of failing
silently. 0.19.20 scope (light-theme scheme 3) and 0.19.19 scope (Dayan
projector keys, verifyOperation null-bound outcome, collapsed desktop panel)
still hold. Everything else must match the 0.19.7 baseline."""
from pathlib import Path
import json,hashlib,subprocess,re
r=Path(__file__).resolve().parents[1]
changed={'AppVersion.java','DeckDialogBuilder.java','AuroraStyle.java','AuroraLayout.java','SoftBackdrop.java','GlassSurface.java','DepthSurface.java','RemoteFace.java','RemoteKeyArt.java','VolumeGlide.java','MainActivity.java','DesktopAssistant.java','DesktopDiagnostic.java','OfficialAppSources.java','IrCodes.java','ConnectionService.java'}
new={'HaloSupport.java','HaloDownload.java','HaloInstaller.java','HaloProbe.java','ProjectorKeys.java','KeepalivePlan.java'}
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
 for popped in ['app/src/main/java/cn/linkdeck/adb/MainActivity.java','app/src/main/java/cn/linkdeck/adb/AerialProfiles.java','app/src/main/java/cn/linkdeck/adb/AerialVersionChecks.java','app/src/main/java/cn/linkdeck/adb/DesktopAssistant.java','app/src/main/java/cn/linkdeck/adb/DesktopDiagnostic.java','app/src/main/java/cn/linkdeck/adb/OfficialAppSources.java','app/src/main/java/cn/linkdeck/adb/IrCodes.java','app/src/main/java/cn/linkdeck/adb/ConnectionService.java']:
  expected.pop(popped,None)
 actual=snapshot(r)
 assert expected==actual,'0.19.14 activity/layout/resources/business scope changed'
 main=(r/'app/src/main/java/cn/linkdeck/adb/MainActivity.java').read_text()
 for gone in ['desktopSnapshot','desktopClient','desktopEndpoint','desktopManual','desktopOutcome','desktopDialog','showDesktopPanel()','faultReport()','captureDesktopFault()','browseAerialReleases','DesktopAssistant.inspect(d,e)','DesktopAssistant.permission(dump)']:
  assert gone not in main,f'0.19.14 removed string still present: {gone}'
 for want in ['Halo TV 桌面管理','haloCenter()','checkHalo("auto")','app.halotv.launcher','switchDesktop','DesktopAssistant.HALO','DesktopAssistant.AERIAL','HaloDownload','HaloSupport','HaloInstaller','HaloProbe','pair(c,"检查更新",()->checkAerial("auto"),"桌面助手",()->checkDesktop());','pair(c,"版本管理",()->versionManager(),"故障报告",()->faultReport(DesktopAssistant.AERIAL,aerialDesktop));','切换到 ','华为智慧屏','荣耀智慧屏','长虹','康佳','海尔 / 卡萨帝 / 统帅','海信 / VIDAA / Vidda','乐视','联想','暴风','微鲸','PPTV','To Fac','0、8、1、6','2008','2、5、8、0','本品牌目前无法开启 ADB','未找到本品牌专属的 ADB 开启方法','上、上、下、下、左、右、左、右','账号与安全','左、左、上、右']:
  assert want in main,f'0.19.14 Halo/switch entry missing: {want}'
 for want in ['索尼','三星','Android TV OS build','Tizen','三星电视是 Tizen 系统，没有 ADB']:
  assert want in main,f'0.19.19 Sony/Samsung guide entry missing: {want}'
 for want in ['HaloSupport.FULL,HaloSupport.LITE','非 Aerial / Halo 桌面应用']:
  assert want in main,f'0.19.19 installedProof Halo entry missing: {want}'
 for want in ['对焦设置','梯形校正','更多操作','查看详细诊断','没有可直接修复的缺失项','isObeProjector']:
  assert want in main,f'0.19.19 projector/panel entry missing: {want}'
 proj=(r/'app/src/main/java/cn/linkdeck/adb/ProjectorKeys.java').read_text()
 for want in ['FocusSettingsActivity','KeystoneSettingsActivity','com.obe.settings','am start -n']:
  assert want in proj,f'0.19.19 projector component missing: {want}'
 desk=(r/'app/src/main/java/cn/linkdeck/adb/DesktopAssistant.java').read_text()
 assert '配置已写入，绑定状态未确认' in desk,f'0.19.19 verifyOperation null-bound outcome missing'
 style=(r/'app/src/main/java/cn/linkdeck/adb/AuroraStyle.java').read_text()
 for want in ['0xff67e8f9','0xff38bdf8','0xff818cf8','0xff0b2a3d','primaryText()','frameStart()','frameEnd()','SOFT_START','SOFT_MID','SOFT_END','SOFT_TEXT']:
  assert want in style,f'0.19.20 scheme 3 token missing in AuroraStyle: {want}'
 assert 'light()?newint[]{SOFT_START,SOFT_MID,SOFT_END}' in style.replace(' ',''),f'0.19.20 primaryStops light branch missing'
 assert 'PRIMARY_TEXT==0xff03211b' in style or 'PRIMARY_TEXT=0xff03211b' in style,f'0.19.20 dark-theme primary ink constant must stay'
 glow=(r/'app/src/main/java/cn/linkdeck/adb/AuroraGlow.java').read_text()
 assert 'frameGradient' in glow and 'frameStart()' in glow and 'frameEnd()' in glow,f'0.19.20 frameGradient missing in AuroraGlow'
 glass=(r/'app/src/main/java/cn/linkdeck/adb/GlassSurface.java').read_text()
 assert 'frameGradient' in glass,f'0.19.20 hero card frame must use frameGradient'
 assert 'AuroraStyle.PRIMARY_TEXT' not in main,f'0.19.20 MainActivity must not use PRIMARY_TEXT directly'
 assert main.count('primaryText()')>=4,f'0.19.20 primaryText sites missing in MainActivity'
 checks=(r/'tests/AuroraChecks.java').read_text()
 for want in ['0xff67e8f9','0xff38bdf8','0xff818cf8','primaryText()','frameStart()==0xff67e8f9']:
  assert want in checks,f'0.19.20 AuroraChecks light assertion missing: {want}'
 svc=(r/'app/src/main/java/cn/linkdeck/adb/ConnectionService.java').read_text()
 for want in ['START_STICKY','REFRESH','repost','lastStartError','KeepalivePlan.HEARTBEAT_MS','scheduleWithFixedDelay','正在重新连接','postConnectingNotification','tlsPinKey','pairedHostKey','needsTls']:
  assert want in svc,f'0.19.21 keepalive token missing in ConnectionService: {want}'
 assert 'START_NOT_STICKY' in svc,f'0.19.21 STOP path must stay non-sticky'
 plan=(r/'app/src/main/java/cn/linkdeck/adb/KeepalivePlan.java').read_text()
 for want in ['HEARTBEAT_MS = 60000','HEALTH_TIMEOUT_MS = 5000','tls-pin:','paired-host:','needsTls']:
  assert want in plan,f'0.19.21 keepalive plan token missing: {want}'
 assert 'isFinishing()) ConnectionService.disconnect' not in main,f'0.19.21 swipe-away must not disconnect'
 for want in ['ACTION_APP_NOTIFICATION_SETTINGS','notificationGuideShown','去开启通知','ConnectionService.repost(this)','后台服务：','通知权限：']:
  assert want in main,f'0.19.21 notification guidance missing in MainActivity: {want}'
 assert 'ConnectionService.disconnect(this)' in main,f'0.19.21 explicit disconnect path must stay'
 keep=(r/'tests/KeepaliveChecks.java').read_text()
 assert 'HEARTBEAT_MS == 60000' in keep and 'tlsPinKey' in keep,f'0.19.21 KeepaliveChecks missing'
 print(f'UI 0.19.21 SCOPE PASSED: {len(actual)} exact baseline entries; XML, permissions, event logic unchanged (MainActivity, DesktopAssistant, DesktopDiagnostic, OfficialAppSources, IrCodes are in the allowed set for this release)')
 subprocess.run(['python3',str(r/'tests/dialog_tint_test.py')],check=True)
