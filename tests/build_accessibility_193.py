"""Build optional same-signed Android instrumentation; no production APK changes."""
from pathlib import Path
import subprocess,zipfile
r=Path(__file__).resolve().parents[1];tc=(r/'.toolchain').resolve();bt=tc/'build-tools-36/android-16';android=tc/'platform/android-36/android.jar';out=r/'build/a11y193';out.mkdir(parents=True,exist_ok=True)
for name in ('classes','dex'):(out/name).mkdir(exist_ok=True)
def run(*args):subprocess.run([str(x) for x in args],check=True)
run('java','-jar',tc/'ecj.jar','-8','-nowarn','-encoding','UTF-8','-bootclasspath',str(android)+':'+str(bt/'core-lambda-stubs.jar'),'-classpath',r/'build/classes','-d',out/'classes',r/'tests/runtime/Accessibility193Checks.java')
with zipfile.ZipFile(out/'classes.jar','w') as z:
 for f in (out/'classes').rglob('*.class'):z.write(f,f.relative_to(out/'classes'))
run('java','-cp',tc/'r8-8.6.27.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',android,'--output',out/'dex',out/'classes.jar')
manifest=out/'AndroidManifest.xml';manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.linkdeck.a11yqa"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/><application android:label="Accessibility test"/><instrumentation android:name="cn.linkdeck.qa.Accessibility193Checks" android:targetPackage="cn.linkdeck.adb"/></manifest>')
run(bt/'aapt2','link','-o',out/'unsigned.apk','-I',android,'--manifest',manifest)
with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(out/'dex/classes.dex','classes.dex')
run(bt/'apksigner','sign','--ks',r/'signing/linkdeck-test.jks','--ks-key-alias','linkdeck','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'qa.apk',out/'unsigned.apk')
print('Instrumentation ready:',out/'qa.apk')
