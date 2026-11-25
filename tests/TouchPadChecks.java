package cn.linkdeck.adb;
public class TouchPadChecks {
 static int n;static void ok(boolean b,String message){if(!b)throw new AssertionError(message);n++;System.out.println("PASS: "+message);}
 public static void main(String[] args){
  float t=TouchPadLogic.SWIPE_THRESHOLD_DP;
  ok(TouchPadLogic.direction(0,0,t)==0,"no movement sends nothing");
  ok(TouchPadLogic.direction(t,0,t)==22,"swipe right is KEYCODE_DPAD_RIGHT");
  ok(TouchPadLogic.direction(-t,0,t)==21,"swipe left is KEYCODE_DPAD_LEFT");
  ok(TouchPadLogic.direction(0,t,t)==20,"swipe down is KEYCODE_DPAD_DOWN");
  ok(TouchPadLogic.direction(0,-t,t)==19,"swipe up is KEYCODE_DPAD_UP");
  ok(TouchPadLogic.direction(t-1,0,t)==0,"just under threshold sends nothing");
  ok(TouchPadLogic.direction(t,t/2,t)==22,"dominant horizontal axis wins");
  ok(TouchPadLogic.direction(t/2,t,t)==20,"dominant vertical axis wins");
  float[] rest=TouchPadLogic.consume(2*t,5,t);
  ok(Math.abs(rest[0]-t)<0.001f&&rest[1]==0f,"consume keeps one threshold of remainder on the dominant axis");
  float[] rest2=TouchPadLogic.consume(3,-2*t,t);
  ok(rest2[0]==0f&&Math.abs(rest2[1]+t)<0.001f,"consume keeps vertical remainder when vertical dominates");
  ok(TouchPadLogic.isTap(200,5,TouchPadLogic.TAP_MAX_DP),"short still press is a tap");
  ok(!TouchPadLogic.isTap(400,5,TouchPadLogic.TAP_MAX_DP),"press past tap time is not a tap");
  ok(!TouchPadLogic.isTap(200,TouchPadLogic.TAP_MAX_DP+1,TouchPadLogic.TAP_MAX_DP),"press that moved is not a tap");
  ok(TouchPadLogic.isLongPress(600,5,TouchPadLogic.TAP_MAX_DP),"held still press is a long press");
  ok(!TouchPadLogic.isLongPress(400,5,TouchPadLogic.TAP_MAX_DP),"press under long-press time is not a long press");
  ok(!TouchPadLogic.isLongPress(600,TouchPadLogic.TAP_MAX_DP+1,TouchPadLogic.TAP_MAX_DP),"press that moved is not a long press");
  ok(TouchPadLogic.SWIPE_THRESHOLD_DP==48f,"swipe threshold is 48dp");
  ok(TouchPadLogic.TAP_MAX_DP==12f,"tap movement limit is 12dp");
  ok(TouchPadLogic.TAP_MAX_MS==350,"tap time limit is 350ms");
  ok(TouchPadLogic.LONG_PRESS_MS==500,"long-press time is 500ms");
  ok(TouchPadLogic.REPEAT_MIN_MS==110,"repeat interval floor is 110ms");
  System.out.println("TOUCHPAD PASSED: "+n+" checks.");
 }
}
