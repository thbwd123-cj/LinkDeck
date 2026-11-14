"""Host-side failure-path tests. These do not substitute for Android UI/TV tests."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/cn/linkdeck/adb'
out=root/'build/test-journal'
tc=root/'.toolchain'
def run(*args): subprocess.run([str(x) for x in args],check=True,cwd=root)
run('java','-jar',tc/'ecj.jar','-8','-nowarn','-cp',tc/'json.jar','-d',out,
    root/'tests/stubs/android/content/SharedPreferences.java',root/'tests/stubs/android/content/Context.java',
    src/'DeviceBook.java',src/'TaskJournal.java',root/'tests/TaskJournalFailureChecks.java',root/'tests/DeviceBookRecoveryChecks.java')
for name in ['DeviceBookRecoveryChecks','TaskJournalFailureChecks']:
    run('java','-cp',str(out)+':'+str(tc/'json.jar'),'cn.linkdeck.adb.'+name)
launch=root/'build/test-launch'
run('java','-jar',tc/'ecj.jar','-8','-nowarn','-encoding','UTF-8','-d',launch,
    src/'AdbIdentity.java',src/'AdbClient.java',src/'ApplicationLauncher.java',root/'tests/LauncherCommandPrint.java')
run('python3',root/'tests/launcher_confirmation_test.py')
run('java','-jar',tc/'ecj.jar','-8','-nowarn','-d',out,src/'HoldRepeater.java',root/'tests/HoldRepeaterChecks.java')
run('java','-cp',out,'cn.linkdeck.adb.HoldRepeaterChecks')
print('FOCUSED 0.19.2 CHECKS PASSED')
