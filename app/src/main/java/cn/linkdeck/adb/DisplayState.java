package cn.linkdeck.adb;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Preserve pre-existing Android wm overrides rather than assuming defaults. */
public final class DisplayState {
  private DisplayState() {}

  public static String restoreCommand(String before) {
    if (!before.contains("Physical size:") || !before.contains("Physical density:")) return null;
    Matcher size = Pattern.compile("Override size:\\s*([0-9]+x[0-9]+)").matcher(before);
    Matcher dpi = Pattern.compile("Override density:\\s*([0-9]+)").matcher(before);
    return "wm size "
        + (size.find() ? size.group(1) : "reset")
        + " && wm density "
        + (dpi.find() ? dpi.group(1) : "reset");
  }
}
