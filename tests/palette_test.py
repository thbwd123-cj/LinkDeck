from pathlib import Path
import subprocess,xml.etree.ElementTree as E
r=Path(__file__).resolve().parents[1];out=r/'build/palette-test';out.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-jar',str(r/'.toolchain/ecj.jar'),'-8','-nowarn','-encoding','UTF-8','-d',str(out),str(r/'app/src/main/java/cn/linkdeck/adb/UiPalette.java'),str(r/'tests/PaletteChecks.java')],check=True)
subprocess.run(['java','-cp',str(out),'cn.linkdeck.adb.PaletteChecks'],check=True)
for i in range(1,5):
 root=E.parse(r/f'app/src/main/res/values/palette_{i}.xml').getroot()
 names={x.attrib['name'] for x in root}
 assert f'PaletteTheme{i}' in names and f'PaletteDialog{i}' in names
 for item in root.iter('item'):
  if item.text.startswith('@drawable/'):
   assert (r/'app/src/main/res/drawable'/(item.text.split('/')[-1]+'.xml')).is_file()
main=(r/'app/src/main/java/cn/linkdeck/adb/MainActivity.java').read_text()
section=main.split('private void choosePalette()',1)[1].split('private boolean',1)[0]
assert 'putString("color_theme"' in section and 'show(page)' in section
assert 'recreate()' not in section and '.shell(' not in section and 'adb.close' not in section
print('PALETTE RESOURCES AND CONNECTION-PRESERVING SWITCH CHECKED')
