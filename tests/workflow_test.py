"""Reproduce 0.7 logic checks with the project's ECJ and official aapt2 toolchain."""
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[1]
tc=r/'.toolchain'; bt=tc/'build-tools/android-15'; out=r/'build/workflow-fixtures'; classes=r/'build/test-classes'
out.mkdir(parents=True,exist_ok=True);classes.mkdir(parents=True,exist_ok=True)
def run(*args,**kwargs):
    command=Path(str(args[0]))
    if command.is_file():command.chmod(command.stat().st_mode|0o111)
    subprocess.run([str(x) for x in args],check=True,**kwargs)
base=(r/'tests/runtime/SplitBaseManifest.xml').read_text();part=(r/'tests/runtime/SplitPartManifest.xml').read_text()
for name,xml in [('base',base),('split',part),('required',base.replace('<application ','<application android:isSplitRequired="true" ')),('dependent',part.replace('split="config.qa"','split="feature.extra"').replace('<application ','<uses-split android:name="config.qa"/><application '))]:
    manifest=out/(name+'.xml');manifest.write_text(xml)
    run(bt/'aapt2','link','-o',out/(name+'.apk'),'-I',tc/'platform/android-35/android.jar','--manifest',manifest)
src=r/'app/src/main/java/cn/linkdeck/adb'
run('java','-jar',tc/'ecj.jar','-8','-nowarn','-encoding','UTF-8','-d',classes,*[src/(n+'.java') for n in ['AdbIdentity','AdbClient','ApkTransfer','ApkManifest','ConnectionDiagnostic','DisplayState']],*[r/'tests'/(n+'.java') for n in ['WorkflowChecks','ApkTransferChecks','ConnectionChecks','LegacyShellChecks','ClientIntegration']])
run('java','-cp',classes,'cn.linkdeck.adb.WorkflowChecks',out)
run('java','-cp',classes,'cn.linkdeck.adb.ApkTransferChecks')
run('python',r/'tests/connection_test.py')
run('python',r/'tests/legacy_shell_test.py')
