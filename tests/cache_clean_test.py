from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[1];tc=r/'.toolchain';out=r/'build/cache-test';out.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-jar',str(tc/'ecj.jar'),'-8','-nowarn','-encoding','UTF-8','-d',str(out),str(r/'app/src/main/java/cn/linkdeck/adb/CacheCleaner.java'),str(r/'tests/CacheCleanerChecks.java')],check=True)
subprocess.run(['java','-cp',str(out),'cn.linkdeck.adb.CacheCleanerChecks'],check=True)

main=(r/'app/src/main/java/cn/linkdeck/adb/MainActivity.java').read_text()
assert 'addButton(install,"一键清理",false,()->cleanCache())' in main
assert 'addButton(cleanup,"一键清理",false,()->cleanCache())' in main
assert '"pm uninstall --user 0 " + AdbClient.quote(a.pkg)' in main
flow=main.split('private void cleanCache(){',1)[1].split('private void openTvStorageSettings()',1)[0]
assert 'if(!requestConnection())return;' in flow
assert 'pm clear' not in flow and 'pm uninstall' not in flow
assert 'after<=outcome[0].before' in flow
for folder in ['app/src/main','tests']:
 for p in (r/folder).rglob('*'):
  if p.suffix in ['.java','.json','.txt']:
   assert 'Debloat' not in p.name and 'debloat_catalog' not in p.name
   if p.suffix=='.java':assert '去臃肿' not in p.read_text() and 'DebloatEngine' not in p.read_text()
print('CACHE CLEANER UI ROUTING / REMOVAL CHECKS PASSED')
