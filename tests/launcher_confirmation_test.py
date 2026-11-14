from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
command=subprocess.check_output(['java','-cp',str(root/'build/test-launch'),'cn.linkdeck.adb.LauncherCommandPrint'],text=True)
with tempfile.TemporaryDirectory() as temp:
    folder=Path(temp)
    scripts={
      'monkey':'#!/bin/sh\nif [ "$TEST_MODE" = fail ]; then echo Failed; exit 1; fi\necho "Events injected: 1"\n',
      'dumpsys':'#!/bin/sh\nif [ "$TEST_MODE" = foreground ]; then echo "mResumedActivity: ActivityRecord test.app/.Main"; fi\n',
      'pidof':'#!/bin/sh\n[ "$TEST_MODE" = process ] && exit 0\nexit 1\n',
      'ps':'#!/bin/sh\necho "USER PID NAME"\n',
      'sleep':'#!/bin/sh\nexit 0\n'}
    for name,content in scripts.items():
        p=folder/name;p.write_text(content);p.chmod(0o755)
    for mode,expected in [('foreground','已确认目标应用处于前台'),('process','目标应用进程存在，前台状态未确认'),('unknown','未确认前台或进程'),('fail','Failed')]:
        env=dict(os.environ,PATH=f'{folder}:/usr/bin:/bin',TEST_MODE=mode)
        result=subprocess.run(['/bin/sh','-c',command],env=env,text=True,capture_output=True)
        assert expected in result.stdout,(mode,result.stdout,result.stderr)
        assert (result.returncode!=0)==(mode=='fail'),(mode,result.returncode)
        print('PASS: launcher',mode)
print('LAUNCHER CONFIRMATION PASSED')
