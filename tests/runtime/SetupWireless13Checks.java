package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import android.view.accessibility.*;
/** UI-only preparation on the disposable emulator; leaves live pairing window open. */
public class SetupWireless13Checks extends AerialUiChecks {
 boolean activated;public void onCreate(Bundle args){activated=args!=null&&"true".equals(args.getString("activated"));super.onCreate(args);}
 String texts(AccessibilityNodeInfo n){if(n==null)return "";StringBuilder b=new StringBuilder();if(n.getText()!=null)b.append(n.getText()).append("\n");for(int i=0;i<n.getChildCount();i++)b.append(texts(n.getChild(i)));return b.toString();}
 boolean click(String label){AccessibilityNodeInfo root=appRoot();if(root==null)return false;for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(label)){if(!n.isVisibleToUser())continue;while(n!=null){if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;n=n.getParent();}}return false;}
 boolean scroll(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scroll(n.getChild(i)))return true;return false;}
 void open(String action)throws Exception{getTargetContext().startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(12000);}
 boolean seek(String text)throws Exception{for(int i=0;i<18;i++){if(click(text))return true;scroll(appRoot());Thread.sleep(600);}return false;}
 public void onStart(){Bundle r=new Bundle();try{
  if(!activated){open("android.settings.WIFI_SETTINGS");check(seek("AndroidWifi"),"virtual Wi-Fi network selected in system UI");Thread.sleep(2500);click("Connect");Thread.sleep(2500);
  open("android.settings.DEVICE_INFO_SETTINGS");check(seek("Build number"),"developer activation row found in system UI");for(int i=0;i<7;i++){click("Build number");Thread.sleep(250);}Thread.sleep(1000);}
  open("android.settings.APPLICATION_DEVELOPMENT_SETTINGS");check(seek("Wireless debugging"),"wireless debugging page opened through developer settings");Thread.sleep(1500);
  if(!dialogHas("Pair device with pairing code")){click("Use wireless debugging");Thread.sleep(1500);click("Allow");Thread.sleep(1500);}
  java.util.regex.Matcher port=java.util.regex.Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}:([0-9]{1,5})").matcher(texts(appRoot()));if(!port.find())throw new Exception("Connection port not visible");int connectionPort=Integer.parseInt(port.group(1));
  check(seek("Pair device with pairing code"),"system pairing-code window opened");Thread.sleep(1500);r.putString("stream","WIRELESS SYSTEM UI READY: "+passes+" checks; connectPort="+connectionPort+"; code intentionally not logged.\n");finish(-1,r);
 }catch(Throwable e){r.putString("stream",android.util.Log.getStackTraceString(e));finish(0,r);}}
}
