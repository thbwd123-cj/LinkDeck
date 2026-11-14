package cn.linkdeck.adb;

import java.io.*;

public class LegacyShellChecks {
  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    try (AdbClient c = new AdbClient()) {
      c.connect("127.0.0.1", Integer.parseInt(args[0]), new AdbIdentity(new File(args[1])));
      check(c.shell("echo hello").require().equals("hello"), "builtin echo");
      check(
          c.shell("echo first; echo second").require().equals("first\nsecond"), "multiline output");
      check(c.shell("echo -n no-newline").require().equals("no-newline"), "no trailing newline");
      check(c.shell("false").exitCode == 1, "nonzero preserved");
      check(c.shell("exit 7").exitCode == 7, "subshell exit preserved");
      check(c.shell("echo ok # comment").require().equals("ok"), "trailing comment");
      check(c.shell(":").require().isEmpty(), "empty success");
      AdbClient.Result whitespace = c.shell("echo '  indented'; echo 'tail  '");
      check(whitespace.raw.equals("  indented\ntail  \n"), "preserve output whitespace");
      for (String command : new String[] {"echo MISSING", "echo MALFORMED"}) {
        boolean rejected = false;
        try {
          c.shell(command);
        } catch (IOException e) {
          rejected =
              e instanceof AdbClient.ShellFailure
                  && ((AdbClient.ShellFailure)e).raw.contains(command.substring(5))
                  && e.getMessage().contains("不能确认操作是否成功");
        }
        check(rejected, "incomplete result must not succeed");
      }
      System.out.println(
          "PASS: legacy shell without printf: actual wrapper executes;"
              + " empty/multiline/no-newline/comment output, nonzero/exit status; missing/malformed"
              + " markers fail with raw output");
    }
  }
}
