"""Unified 0.10 host checks. Native Android checks are built separately and run on an emulator."""
from pathlib import Path
import subprocess,os
r=Path(__file__).resolve().parents[1];tc=r/'.toolchain';src=r/'app/src/main/java/cn/linkdeck/adb';out=r/'build/test-classes';out.mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run([str(x) for x in args],check=True)
run('python',r/'tests/workflow_test.py')
run('python',r/'tests/aerial_test.py')
run('python',r/'tests/desktop_test.py')
classes=['TcpCandidateProbe','SubnetAddresses','WifiIpv4','IrCodes','IrVerification','RemoteRoute','GitHubAppRelease','OfficialAppSources','OfficialVlc','OfficialKodi','UpdateScan','AppVersion','ConnectionAddress','DiscoveredDevice','DeviceCapabilities','TlsIdentity','DeviceScope','ApplicationLauncher','PackageProbe','InstallPolicy','AdbIdentity','AdbClient','ApkTransfer','ApkManifest','AerialSupport','AerialSafety','AerialProbe','AerialInstaller','AerialDownload','AerialProfiles','DesktopAssistant','DesktopDiagnostic']
checks=['TcpCandidateProbeChecks','SubnetAddressesChecks','WifiIpv4Checks','IrCodesChecks','GitHubAppReleaseChecks','OfficialSourceChecks','RemoteRouteChecks','ConnectionAddressChecks','DiscoveryIndexChecks','CompatibilityChecks','UpgradeCoreChecks','DesktopChecks','AerialVersionChecks','AerialProbeChecks','AerialProbeLegacyChecks','DiagnosticChecks','PinnedDownloadChecks','ShellTimeoutChecks']
run('java','-jar',tc/'ecj.jar','-8','-nowarn','-encoding','UTF-8','-classpath',tc/'json.jar','-d',out,*[src/(n+'.java') for n in classes],*[r/'tests'/(n+'.java') for n in checks])
for test in ['TcpCandidateProbeChecks','SubnetAddressesChecks','WifiIpv4Checks','IrCodesChecks','GitHubAppReleaseChecks','OfficialSourceChecks','RemoteRouteChecks','ConnectionAddressChecks','DiscoveryIndexChecks','CompatibilityChecks','UpgradeCoreChecks','AerialVersionChecks','AerialProbeChecks','DiagnosticChecks','PinnedDownloadChecks']:run('java','-cp',str(out)+os.pathsep+str(tc/'json.jar'),'cn.linkdeck.adb.'+test)
run('python',r/'tests/shell_timeout_test.py')
run('python',r/'tests/fake_adb_test.py')
run('python',r/'tests/aerial_probe_legacy_test.py')
run('python',r/'tests/debloat_test.py')
print('UNIFIED HOST CHECKS PASSED')
