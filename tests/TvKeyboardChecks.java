package cn.linkdeck.adb;
public class TvKeyboardChecks {
 static int n;static void ok(boolean b,String message){if(!b)throw new AssertionError(message);n++;System.out.println("PASS: "+message);}
 public static void main(String[] args){
  ok(TvKeyboard.PKG.equals("com.android.adbkeyboard"),"ADBKeyBoard package matches the official project");
  ok(TvKeyboard.IME.equals("com.android.adbkeyboard/.AdbIME"),"IME component matches the official project");
  ok(TvKeyboard.DOWNLOAD_URL.equals("https://github.com/senzhk/ADBKeyBoard/releases/download/v2.4-dev/keyboardservice-debug.apk"),"download URL is the official v2.4-dev release asset");
  ok(TvKeyboard.EXPECTED_SHA256.equals("e0d0cf276b710cb34c46121f58720f5285a83ed410b0d45f57a0677b67dc2852"),"expected SHA-256 matches the verified download");
  ok(TvKeyboard.EXPECTED_SIZE==18595,"expected size matches the verified download");
  ok(TvKeyboard.VERSION_NAME.equals("2.0")&&TvKeyboard.VERSION_CODE==2,"version matches aapt2 badging");
  ok(TvKeyboard.LICENSE.equals("GPLv2"),"license is recorded as GPLv2, so the APK is downloaded on demand, never bundled");
  ok(TvKeyboard.isAscii("youtube 123"),"plain ASCII text is ASCII");
  ok(TvKeyboard.isAscii(""),"empty text is ASCII");
  ok(!TvKeyboard.isAscii("你好"),"Chinese text is not ASCII");
  ok(!TvKeyboard.isAscii("hello你好"),"mixed text is not ASCII");
  ok(TvKeyboard.base64("你好").equals("5L2g5aW9"),"base64 of 你好 is the known vector");
  ok(TvKeyboard.base64("Hi").equals("SGk="),"base64 of Hi is the known vector");
  ok(TvKeyboard.textCommand("youtube").equals("input text 'youtube'"),"ASCII text command is exact");
  ok(TvKeyboard.textCommand("hello world").equals("input text 'hello%sworld'"),"spaces become %s in the text command");
  ok(TvKeyboard.broadcastCommand("你好").equals("am broadcast -a ADB_INPUT_B64 --es msg '5L2g5aW9'"),"Chinese text goes out as a base64 broadcast");
  ok(TvKeyboard.broadcastCommand("Hi 你好").contains("ADB_INPUT_B64"),"mixed text also uses the base64 broadcast");
  ok(TvKeyboard.enableCommand().equals("ime enable com.android.adbkeyboard/.AdbIME"),"enable command is exact");
  ok(TvKeyboard.setCommand().equals("ime set com.android.adbkeyboard/.AdbIME"),"set command is exact");
  ok(TvKeyboard.restoreCommand("com.google.android.inputmethod.pinyin/.PinyinIME").equals("ime set com.google.android.inputmethod.pinyin/.PinyinIME"),"restore command returns to the recorded IME");
  ok(TvKeyboard.installedProbe().equals("pm list packages com.android.adbkeyboard"),"installed probe is exact");
  ok(TvKeyboard.currentImeProbe().equals("settings get secure default_input_method"),"current IME probe is exact");
  ok(TvKeyboard.parseDefaultIme("com.android.adbkeyboard/.AdbIME\n").equals("com.android.adbkeyboard/.AdbIME"),"default IME output is trimmed");
  ok(TvKeyboard.isInstalled("package:com.android.adbkeyboard"),"pm list output detects the installed IME");
  ok(!TvKeyboard.isInstalled("package:com.example.other"),"pm list output without the package is not installed");
  ok(TvKeyboard.isCurrentIme("com.android.adbkeyboard/.AdbIME\n"),"current IME check matches with trailing newline");
  ok(!TvKeyboard.isCurrentIme("com.google.android.inputmethod.pinyin/.PinyinIME"),"another IME is not the ADBKeyBoard IME");
  System.out.println("TV KEYBOARD PASSED: "+n+" checks.");
 }
}
