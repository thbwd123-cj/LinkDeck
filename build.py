#!/usr/bin/env python3
"""Build a small native Android APK using official Android build-tools and ECJ.
Requires Python 3, Java 17+, internet for first bootstrap. No Gradle required.
Usage: python3 build.py [--toolchain /path/to/existing/toolchain]
"""
import argparse, hashlib, os, pathlib, shutil, subprocess, urllib.request, zipfile, re

ROOT=pathlib.Path(__file__).resolve().parent
VERSION='0.19.19'
assert re.search(r'NAME = \"'+re.escape(VERSION)+r'\"', (ROOT/'app/src/main/java/cn/linkdeck/adb/AppVersion.java').read_text()), 'UI/report version differs from APK version'
parser=argparse.ArgumentParser();parser.add_argument('--toolchain',type=pathlib.Path,default=ROOT/'.toolchain');parser.add_argument('--release-keystore',type=pathlib.Path);parser.add_argument('--release-alias',default='linkdeck');parser.add_argument('--storepass-env',default='LINKDECK_STORE_PASSWORD');parser.add_argument('--keypass-env',default='LINKDECK_KEY_PASSWORD');args=parser.parse_args()
TC=args.toolchain.resolve();TC.mkdir(parents=True,exist_ok=True)
JAVA=shutil.which('java') or '/usr/bin/java'
def run(*args):
    command=pathlib.Path(str(args[0]))
    if command.is_file() and command.resolve().is_relative_to(TC):command.chmod(command.stat().st_mode | 0o111)
    subprocess.run([str(x) for x in args],check=True,cwd=ROOT)
def fetch(url,target):
    if not target.exists():
        print('Downloading',target.name,flush=True);tmp=target.with_suffix('.download');urllib.request.urlretrieve(url,tmp);tmp.replace(target)
def sdk(archive,dest,expected):
    if not expected.exists():
        path=TC/archive;fetch('https://dl.google.com/android/repository/'+archive,path)
        with zipfile.ZipFile(path) as z:z.extractall(dest)
sdk('platform-36_r02.zip',TC/'platform',TC/'platform/android-36/android.jar')
sdk('build-tools_r36_linux.zip',TC/'build-tools-36',TC/'build-tools-36/android-16/lib/d8.jar')
fetch('https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar',TC/'ecj.jar')
BT=TC/'build-tools-36/android-16';ANDROID=TC/'platform/android-36/android.jar'
R8=TC/'r8-8.6.27.jar'
fetch('https://storage.googleapis.com/r8-releases/raw/8.6.27/r8.jar',R8)
if hashlib.sha256(R8.read_bytes()).hexdigest()!='fe1e15d8d1dd2cdb43d6d62c6982534466439d5c1a68889a3aa4acf8a944393f':raise RuntimeError('R8/D8 8.6.27 checksum mismatch')
for name in ['aapt2','aapt','d8','apksigner','zipalign']:(BT/name).chmod(0o755)
BUILD=ROOT/'build';DIST=ROOT/'dist';BUILD.mkdir(exist_ok=True);DIST.mkdir(exist_ok=True)
for d in ['classes','dex']:
    if (BUILD/d).exists():shutil.rmtree(BUILD/d)
    (BUILD/d).mkdir()
# Build the independent read-only metadata helper for Android 5.0+ targets.
for name in ['helper-classes','helper-dex']:
    path=BUILD/name
    if path.exists():shutil.rmtree(path)
    path.mkdir()
run(JAVA,'-jar',TC/'ecj.jar','-8','-nowarn','-encoding','UTF-8','-bootclasspath',str(ANDROID)+os.pathsep+str(BT/'core-lambda-stubs.jar'),'-d',BUILD/'helper-classes',*list((ROOT/'helper/src').rglob('*.java')))
with zipfile.ZipFile(BUILD/'helper-classes.jar','w') as z:
    for p in (BUILD/'helper-classes').rglob('*.class'):z.write(p,p.relative_to(BUILD/'helper-classes'))
run(JAVA,'-cp',R8,'com.android.tools.r8.D8','--release','--min-api','21','--lib',ANDROID,'--output',BUILD/'helper-dex',BUILD/'helper-classes.jar')
assets=ROOT/'app/src/main/assets';assets.mkdir(exist_ok=True)
with zipfile.ZipFile(assets/'catalog.jar','w',zipfile.ZIP_DEFLATED) as z:z.write(BUILD/'helper-dex/classes.dex','classes.dex')
sources=list((ROOT/'app/src/main/java').rglob('*.java'))
CONSCRYPT=ROOT/'third_party/conscrypt/classes.jar'
SPAKE=ROOT/'third_party/spake2/classes.jar'
if hashlib.sha256((ROOT/'third_party/spake2/spake2-android-2.2.1.aar').read_bytes()).hexdigest()!='8798fb6c04b5d53a6307ed9481e9afe563227abd4e8b9e917c8514fa850071bb':raise RuntimeError('SPAKE dependency checksum mismatch')
if hashlib.sha256((ROOT/'third_party/conscrypt/conscrypt-android-2.5.3.aar').read_bytes()).hexdigest()!='551ae4e301c571760d1791e647db6ed1dcb10d34dcae7aa12b67f220f2ce98d1':raise RuntimeError('Conscrypt dependency checksum mismatch')
run(JAVA,'-jar',TC/'ecj.jar','-8','-nowarn','-encoding','UTF-8','-bootclasspath',str(ANDROID)+os.pathsep+str(BT/'core-lambda-stubs.jar'),'-classpath',str(CONSCRYPT)+os.pathsep+str(SPAKE),'-d',BUILD/'classes',*sources)
with zipfile.ZipFile(BUILD/'classes.jar','w') as z:
    for p in (BUILD/'classes').rglob('*.class'):z.write(p,p.relative_to(BUILD/'classes'))
run(JAVA,'-cp',R8,'com.android.tools.r8.D8','--release','--min-api','26','--lib',ANDROID,'--output',BUILD/'dex',BUILD/'classes.jar',CONSCRYPT,SPAKE)
run(BT/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',BUILD/'resources.zip')
run(BT/'aapt2','link','-o',BUILD/'unsigned.apk','-I',ANDROID,'--manifest',ROOT/'app/src/main/AndroidManifest.xml','--version-code','67','--version-name',VERSION,'-A',ROOT/'app/src/main/assets',BUILD/'resources.zip')
with zipfile.ZipFile(BUILD/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    for p in (BUILD/'dex').glob('*.dex'):z.write(p,p.name)
    for p in (ROOT/'third_party/conscrypt/jni').rglob('*.so'):z.write(p,'lib/'+str(p.relative_to(ROOT/'third_party/conscrypt/jni')))
    for p in (ROOT/'third_party/spake2/jni').rglob('*.so'):z.write(p,'lib/'+str(p.relative_to(ROOT/'third_party/spake2/jni')))
    with zipfile.ZipFile(CONSCRYPT) as dependency:
        for name in dependency.namelist():
            if not name.endswith(('.class','/')) and name!='META-INF/MANIFEST.MF':z.writestr(name,dependency.read(name))
run(BT/'zipalign','-f','4',BUILD/'unsigned.apk',BUILD/'aligned.apk')
KEY=args.release_keystore.resolve() if args.release_keystore else ROOT/'signing/linkdeck-test.jks'
if args.release_keystore and (not KEY.is_file() or not os.environ.get(args.storepass_env) or not os.environ.get(args.keypass_env)):raise RuntimeError('Release keystore and password environment variables must be configured; values are never printed')
if not KEY.exists():
    raise RuntimeError('Signing keystore is missing. Restore the original private keystore before building an update; do not generate a replacement key for an installed app.')
APK=DIST/('LinkDeck-'+VERSION+'.apk')
run(BT/'apksigner','sign','--ks',KEY,'--ks-key-alias',args.release_alias,'--ks-pass',('env:'+args.storepass_env if args.release_keystore else 'pass:android'),'--key-pass',('env:'+args.keypass_env if args.release_keystore else 'pass:android'),'--out',APK,BUILD/'aligned.apk')
run(BT/'apksigner','verify','--verbose',APK)
print('Built:',APK);print('SHA-256:',hashlib.sha256(APK.read_bytes()).hexdigest())
