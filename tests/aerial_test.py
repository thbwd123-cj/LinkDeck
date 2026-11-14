"""Policy/installer checks and network-state simulation; no television connection."""
from pathlib import Path
import subprocess,urllib.request
r=Path(__file__).resolve().parents[1];tc=r/'.toolchain';classes=r/'build/test-classes';classes.mkdir(parents=True,exist_ok=True)
src=r/'app/src/main/java/cn/linkdeck/adb'
json=tc/'json.jar'
if not json.exists():urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',json)
subprocess.run(['java','-jar',str(tc/'ecj.jar'),'-8','-nowarn','-encoding','UTF-8','-classpath',str(json),'-d',str(classes),*[str(src/(n+'.java')) for n in ['AppVersion','AdbIdentity','AdbClient','ApkTransfer','AerialSupport','AerialInstaller','AerialDownload','AerialSafety']],str(r/'tests/AerialChecks.java'),str(r/'tests/AerialDownloadChecks.java')],check=True)
subprocess.run(['java','-cp',str(classes),'cn.linkdeck.adb.AerialChecks'],check=True)
fixture=r/'tests/fixtures/aerial-standard.apk'
if fixture.exists():subprocess.run(['java','-cp',str(classes)+':'+str(json),'cn.linkdeck.adb.AerialDownloadChecks',str(r/'tests/fixtures/aerial-release.json'),str(fixture)],check=True)
else:print('Download state tests require the optional official APK fixture; see tests/fixtures/AERIAL-SOURCE.md')
