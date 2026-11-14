package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import android.graphics.Rect;import android.view.*;import android.view.accessibility.AccessibilityNodeInfo;import android.widget.*;
/** System-dispatched touchscreen events, never reflection-based page navigation. */
public class NavTouchChecks extends RuntimeChecks {
 boolean baseline,navigationOnly;
 public void onCreate(Bundle args){navigationOnly=args!=null&&"true".equals(args.getString("navigationOnly"));baseline=args!=null&&"true".equals(args.getString("baseline"));super.onCreate(args);}
 void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passes++;Bundle b=new Bundle();b.putString("stream","PASS: "+name+"\n");sendStatus(0,b);}
 int current()throws Exception{return (Integer)field("page");}
 View slot(int index)throws Exception{return ((ViewGroup)field("nav")).getChildAt(index);}
 View target(int index)throws Exception{View s=slot(index),hit=s.findViewWithTag("nav-tab-"+index);return hit==null?s:hit;}
 Rect rect(View v){Rect r=new Rect();v.getGlobalVisibleRect(r);return r;}
 void event(int action,float x,float y,long start){MotionEvent e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);boolean sent=getUiAutomation().injectInputEvent(e,true);e.recycle();if(!sent)throw new AssertionError("Touch injection rejected: action="+action+" focus="+activity.hasWindowFocus()+" x="+x+" y="+y);}
 void ready()throws Exception{long end=SystemClock.uptimeMillis()+15000;while(!activity.hasWindowFocus()&&SystemClock.uptimeMillis()<end)Thread.sleep(100);if(!activity.hasWindowFocus())throw new AssertionError("Application window did not regain input focus");Thread.sleep(250);}
 void select(int index,String region)throws Exception{
  ready();
  long deadline=SystemClock.uptimeMillis()+10000;while(rect(target(index)).height()==0&&SystemClock.uptimeMillis()<deadline)Thread.sleep(100);
  View t=target(index);Rect bounds=rect(t);View name=find(slot(index),new String[]{"设备","遥控","应用","文件","工具"}[index]);Rect label=rect(name);
  float x=bounds.exactCenterX(),y=region.equals("label")?label.exactCenterY():region.equals("padding")?bounds.top+3:region.equals("edge")?bounds.right-3:bounds.top+bounds.height()*.3f;
  if(region.equals("edge")){x=bounds.right-3;y=bounds.exactCenterY();}
  long down=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y,down);Thread.sleep(130);
  Rect pressed=rect(t);if(!baseline)check(bounds.equals(pressed),"tab touch target stays fixed while pressed: "+index+"/"+region);
  else {Bundle b=new Bundle();b.putString("stream","BASELINE target "+bounds+" pressed "+pressed+"\n");sendStatus(0,b);}
  event(MotionEvent.ACTION_UP,x,y,down);
  if(baseline){Thread.sleep(1200);check(current()==index,"baseline physical tap must select tab "+index+", actual="+current());}else waitFor("physical "+region+" tap selects tab "+index,()->current()==index);
  Thread.sleep(500);ViewGroup root=(ViewGroup)field("root"),header=(ViewGroup)root.getChildAt(0),brand=(ViewGroup)header.getChildAt(0);String expected=new String[]{"轻连","遥控器","电视应用","文件","工具箱"}[index];check(expected.contentEquals(((TextView)brand.getChildAt(0)).getText()),"visible header agrees with selected tab "+index);
 }
 void tapView(View view)throws Exception{runOnMainSync(()->view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true));Thread.sleep(400);ready();Rect r=rect(view);check(r.width()>0&&r.height()>0,"physical control has visible bounds");long down=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,r.exactCenterX(),r.exactCenterY(),down);Thread.sleep(130);event(MotionEvent.ACTION_UP,r.exactCenterX(),r.exactCenterY(),down);}
 void modalTap(String text)throws Exception{long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=appRoot();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(!text.contentEquals(node.getText())||!node.isVisibleToUser())continue;Rect r=new Rect();node.getBoundsInScreen(r);if(r.width()>0&&r.height()>0){long down=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,r.exactCenterX(),r.exactCenterY(),down);Thread.sleep(130);event(MotionEvent.ACTION_UP,r.exactCenterX(),r.exactCenterY(),down);Thread.sleep(400);return;}}Thread.sleep(200);}throw new AssertionError("No visible modal button "+text);}
 void commonControls()throws Exception{
  select(0,"icon");tapView(find(activity.getWindow().getDecorView(),"安装应用"));waitFor("real touch opens installation chooser",()->dialogHas("选择手机已安装应用"));modalTap("取消");waitFor("real touch cancels installation chooser",()->!dialogHas("选择手机已安装应用"));
  select(1,"icon");tapView(find(activity.getWindow().getDecorView(),"键盘输入"));waitFor("real touch opens keyboard input panel",()->((View)field("remoteInputPanel")).getVisibility()==View.VISIBLE);tapView(find(activity.getWindow().getDecorView(),"键盘输入"));waitFor("real touch collapses keyboard panel",()->((View)field("remoteInputPanel")).getVisibility()==View.GONE);
  tapView(find(activity.getWindow().getDecorView(),"电源 / 待机"));waitFor("real touch opens icon power confirmation",()->dialogHas("切换电源状态"));modalTap("取消");waitFor("real touch cancels standby confirmation",()->!dialogHas("切换电源状态"));
  tapView(find(activity.getWindow().getDecorView(),"主页"));waitFor("real touch activates round HOME control without bypassing connection gate",()->dialogHas("连接后即可操作"));modalTap("继续浏览");
  select(4,"label");tapView(find(activity.getWindow().getDecorView(),"任务与记录"));waitFor("real touch opens ordinary task button",()->dialogHas("清除已结束"));modalTap("关闭");waitFor("real touch closes task sheet",()->!dialogHas("清除已结束"));select(0,"icon");
 }
 public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).putBoolean("reduce_motion",false).commit();
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(1600);
  if(navigationOnly)check(Math.abs(activity.getResources().getConfiguration().fontScale-1.3f)<.03f,"large font configuration is actually active");
  for(String area:new String[]{"icon","label","padding","edge"})for(int index:new int[]{1,2,3,4,0})select(index,area);
  if(navigationOnly){result.putString("stream","NATIVE LARGE-FONT TAB TOUCH PASSED: "+passes+" checks.\n");finish(-1,result);return;}
  if(!baseline){commonControls();View hit=target(1);check(hit instanceof Button&&hit.isClickable()&&hit.isFocusable()&&!hit.isFocusableInTouchMode(),"navigation uses a native button with predictable touch focus");
   Rect b=rect(hit);long down=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,b.exactCenterX(),b.exactCenterY(),down);event(MotionEvent.ACTION_CANCEL,b.exactCenterX(),b.exactCenterY(),down);Thread.sleep(400);check(current()==0,"cancelled navigation gesture does not switch tabs");
   runOnMainSync(()->targetClick(2));waitFor("accessibility-style click selects applications",()->current()==2);
   getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("reduce_motion",true).commit();select(3,"icon");check(((View)field("body")).getAnimation()==null,"reduced-motion navigation works without entry animation");select(0,"label");
   getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("reduce_motion",false).commit();select(1,"icon");Thread.sleep(1600);shot("v0113-remote");select(0,"label");Thread.sleep(1500);shot("v0113-home");
  }
  result.putString("stream","NATIVE TAB TOUCH PASSED: "+passes+" checks.\n");finish(-1,result);
 }catch(Throwable t){try{shot("tab-touch-failure");}catch(Exception ignored){}result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}}
 void targetClick(int index){try{target(index).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null);}catch(Exception e){throw new RuntimeException(e);}}
}
