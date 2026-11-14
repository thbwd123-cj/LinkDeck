"""Deterministic desktop repair/restore tests; no TV connection or settings changes."""
from pathlib import Path
import subprocess,urllib.request
r=Path(__file__).resolve().parents[1];tc=r/'.toolchain';classes=r/'build/test-classes';classes.mkdir(parents=True,exist_ok=True)
json=tc/'json.jar'
if not json.exists():urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',json)
src=r/'app/src/main/java/cn/linkdeck/adb'
subprocess.run(['java','-jar',str(tc/'ecj.jar'),'-8','-nowarn','-encoding','UTF-8','-classpath',str(json),'-d',str(classes),*[str(src/(n+'.java')) for n in ['AppVersion','AdbIdentity','AdbClient','AerialSupport','DesktopAssistant']],str(r/'tests/DesktopChecks.java'),str(r/'tests/DesktopRaceChecks.java'),str(r/'tests/DesktopVerificationChecks.java')],check=True)
subprocess.run(['java','-cp',str(classes)+':'+str(json),'cn.linkdeck.adb.DesktopChecks'],check=True)

subprocess.run(['java','-cp',str(classes)+':'+str(json),'cn.linkdeck.adb.DesktopRaceChecks'],check=True)

subprocess.run(['java','-cp',str(classes)+':'+str(json),'cn.linkdeck.adb.DesktopVerificationChecks'],check=True)
