"""Host safety regression: curated data, protections, failure honesty and recovery."""
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[1];tc=r/'.toolchain';src=r/'app/src/main/java/cn/linkdeck/adb';out=r/'build/debloat-tests';out.mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run([str(a) for a in args],check=True)
run('java','-jar',tc/'ecj.jar','-8','-nowarn','-encoding','UTF-8','-cp',tc/'json.jar','-d',out,src/'DebloatCatalog.java',src/'DebloatEngine.java',src/'DebloatIdentity.java',r/'tests/DebloatChecks.java')
run('java','-cp',str(out)+':'+str(tc/'json.jar'),'cn.linkdeck.adb.DebloatChecks',r/'app/src/main/assets/debloat_catalog.json')
