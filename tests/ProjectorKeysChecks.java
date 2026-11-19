package cn.linkdeck.adb;
import java.util.*;
public class ProjectorKeysChecks {
 static int n;static void ok(boolean b,String message){if(!b)throw new AssertionError(message);n++;System.out.println("PASS: "+message);}
 public static void main(String[] args){
  Map<String,String> obe=new HashMap<>();obe.put("ro.product.model","OBE_NEW X7");obe.put("ro.product.brand","OBE");
  ok(ProjectorKeys.isObeProjector(obe),"OBE_NEW X7 is a Dayan projector");
  Map<String,String> brandOnly=new HashMap<>();brandOnly.put("ro.product.brand","obe");
  ok(ProjectorKeys.isObeProjector(brandOnly),"lowercase obe brand matches");
  Map<String,String> skyworth=new HashMap<>();skyworth.put("ro.product.model","8H66 H7");skyworth.put("ro.product.brand","Skyworth");
  ok(!ProjectorKeys.isObeProjector(skyworth),"Skyworth 8H66 H7 is not a Dayan projector");
  ok(!ProjectorKeys.isObeProjector(null),"null properties never match");
  ok(!ProjectorKeys.isObeProjector(new HashMap<>()),"empty properties never match");
  ok(ProjectorKeys.FOCUS.equals("com.obe.settings/com.obe.settings.projector.FocusSettingsActivity"),"focus component matches user screenshot");
  ok(ProjectorKeys.KEYSTONE.equals("com.obe.settings/com.obe.settings.projector.KeystoneSettingsActivity"),"keystone component matches user screenshot");
  ok(ProjectorKeys.launchCommand(ProjectorKeys.FOCUS).equals("am start -n com.obe.settings/com.obe.settings.projector.FocusSettingsActivity"),"focus launch command is exact");
  ok(ProjectorKeys.launchCommand(ProjectorKeys.KEYSTONE).equals("am start -n com.obe.settings/com.obe.settings.projector.KeystoneSettingsActivity"),"keystone launch command is exact");
  System.out.println("PROJECTOR KEYS PASSED: "+n+" checks.");
 }
}
