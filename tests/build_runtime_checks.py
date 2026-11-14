"""Build optional checks for an isolated Android 9 emulator, after build.py."""
from pathlib import Path
import argparse
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--toolchain', type=Path, default=ROOT / '.toolchain')
args = parser.parse_args()
tc = args.toolchain.resolve()
bt = tc / 'build-tools-36/android-16'
android = tc / 'platform/android-36/android.jar'
out = ROOT / 'build/runtime-qa'
sources = ROOT / 'tests/runtime'
for name in ['classes', 'dex']:
    (out / name).mkdir(parents=True, exist_ok=True)

def run(*args):
    command=Path(str(args[0]))
    if command.is_file():command.chmod(command.stat().st_mode | 0o111)
    subprocess.run([str(v) for v in args], check=True)

run('java', '-jar', tc/'ecj.jar', '-8', '-nowarn', '-encoding', 'UTF-8',
    '-bootclasspath', str(android) + ':' + str(bt/'core-lambda-stubs.jar'),
    '-classpath', ROOT/'build/classes', '-d', out/'classes', sources/'RuntimeChecks.java', sources/'UpgradeChecks.java', sources/'ConsoleChecks.java', sources/'IncompleteConsoleChecks.java', sources/'SoftUiChecks.java', sources/'WorkflowUiChecks.java', sources/'AerialUiChecks.java', sources/'AerialInstallRuntimeChecks.java', sources/'DesktopUiChecks.java', sources/'DesktopFixUiChecks.java', sources/'DesktopSystemChecks.java', sources/'Version010UiChecks.java', sources/'Version010SystemChecks.java', sources/'Version011UiChecks.java', sources/'Version011RemoteChecks.java', sources/'RenderProbeChecks.java',sources/'Version012MotionChecks.java',sources/'NavTouchChecks.java',sources/'AuditChecks.java',sources/'TransferUiChecks.java',sources/'Version012Checks.java',sources/'Crypto13Checks.java',sources/'Wireless13Checks.java',sources/'SetupWireless13Checks.java',sources/'CompatibilityUiChecks.java',sources/'Discovery13Checks.java',sources/'Preview13Checks.java',sources/'Cover13Checks.java')
with zipfile.ZipFile(out/'classes.jar', 'w') as z:
    for f in (out/'classes').rglob('*.class'):
        z.write(f, f.relative_to(out/'classes'))
run(bt/'d8', '--min-api', '26', '--lib', android, '--output', out/'dex', out/'classes.jar')

def sign(source, destination):
    run(bt/'apksigner', 'sign', '--ks', ROOT/'signing/linkdeck-test.jks',
        '--ks-key-alias', 'linkdeck', '--ks-pass', 'pass:android', '--key-pass',
        'pass:android', '--out', destination, source)
    run(bt/'apksigner','verify',destination)

run(bt/'aapt2', 'link', '-o', out/'target-unsigned.apk', '-I', android,
    '--manifest', sources/'TargetManifest.xml')
sign(out/'target-unsigned.apk', out/'target.apk')
run(bt/'aapt2','link','-o',out/'desktop-fixture-unsigned.apk','-I',android,'--manifest',sources/'DesktopFixtureManifest.xml')
sign(out/'desktop-fixture-unsigned.apk',out/'desktop-fixture.apk')
run(bt/'aapt2','link','-o',out/'desktop-newer-unsigned.apk','-I',android,'--manifest',sources/'DesktopNewerManifest.xml')
sign(out/'desktop-newer-unsigned.apk',out/'desktop-newer.apk')
# Isolated update and TV-only fixtures, never included in the production app.
for version in [1,2,3]:
    manifest=out/('upgrade-v'+str(version)+'.xml')
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.linkdeck.upgradefixture" android:versionCode="'+str(version)+'" android:versionName="'+str(version)+'.0"><uses-sdk android:minSdkVersion="21" android:targetSdkVersion="28"/><application android:debuggable="true" android:hasCode="false" android:label="Upgrade fixture"/></manifest>')
    run(bt/'aapt2','link','-o',out/('upgrade-v'+str(version)+'-unsigned.apk'),'-I',android,'--manifest',manifest)
    sign(out/('upgrade-v'+str(version)+'-unsigned.apk'),out/('upgrade-v'+str(version)+'.apk'))
other=out/'qa-other.jks'
if not other.exists():run('keytool','-genkeypair','-keystore',other,'-storepass','android','-keypass','android','-alias','other','-keyalg','RSA','-validity','365','-dname','CN=Isolated QA','-noprompt')
run(bt/'apksigner','sign','--ks',other,'--ks-pass','pass:android','--key-pass','pass:android','--out',out/'upgrade-wrong.apk',out/'upgrade-v2-unsigned.apk')
tv=out/'tv-only.xml';tv.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="cn.linkdeck.tvfixture"><uses-sdk android:minSdkVersion="21" android:targetSdkVersion="28"/><application android:hasCode="false" android:label="TV-only QA"><activity android:name="android.app.Activity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter></activity></application></manifest>')
run(bt/'aapt2','link','-o',out/'tv-only-unsigned.apk','-I',android,'--manifest',tv);sign(out/'tv-only-unsigned.apk',out/'tv-only.apk')

run(bt/'aapt2', 'link', '-o', out/'unsigned.apk', '-I', android,
    '--manifest', sources/'AndroidManifest.xml')
with zipfile.ZipFile(out/'unsigned.apk', 'a') as z:
    z.write(out/'dex/classes.dex', 'classes.dex')
    z.write(out/'target.apk', 'assets/target.apk')
    for name in ['upgrade-v1.apk','upgrade-v2.apk','upgrade-v3.apk','upgrade-wrong.apk','tv-only.apk']:z.write(out/name,'assets/'+name)
    z.write(out/'desktop-fixture.apk','assets/desktop-fixture.apk')
    z.write(out/'desktop-newer.apk','assets/desktop-newer.apk')
    # Optional real official Aerial release fixture; never included in the user app.
    fixtures = ROOT/'tests/fixtures'
    for name in ['aerial-release.json','aerial-standard.apk']:
        if (fixtures/name).exists():z.write(fixtures/name,'assets/'+name)
sign(out/'unsigned.apk', out/'qa.apk')

# Synthetic base + config split for complete-session installation checks.
for name in ["Base", "Part"]:
    run(bt/"aapt2", "link", "-o", out/("split-"+name.lower()+"-unsigned.apk"), "-I", android, "--manifest", sources/("Split"+name+"Manifest.xml"))
    sign(out/("split-"+name.lower()+"-unsigned.apk"), out/("split-"+name.lower()+".apk"))

print('Optional emulator checks:', out/'qa.apk')
