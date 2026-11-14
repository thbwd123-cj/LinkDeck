package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.graphics.*;import java.io.*;
public class RenderProbeChecks extends RuntimeChecks {
 public void onStart(){Bundle result=new Bundle();try{
 getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).commit();activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(1500);
 for(boolean reduce:new boolean[]{true,false,true,false}){
 getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("reduce_motion",reduce).commit();call("show",new Class[]{int.class},0);Thread.sleep(600);call("show",new Class[]{int.class},1);Thread.sleep(2000);
 View ok=find(activity.getWindow().getDecorView(),"确定");Rect b=new Rect();boolean visible=ok.getGlobalVisibleRect(b);View body=(View)field("body"),scroll=(View)body.getParent();Bitmap image=getUiAutomation().takeScreenshot();int colored=0;for(int y=Math.max(0,b.top);y<Math.min(image.getHeight(),b.bottom);y+=4)for(int x=Math.max(0,b.left);x<Math.min(image.getWidth(),b.right);x+=4){int color=image.getPixel(x,y);if(Color.green(color)>90&&Color.green(color)>Color.red(color)+20)colored++;}
 String line="reduce="+reduce+" visible="+visible+" bounds="+b+" colored="+colored+" body="+body.getWidth()+"x"+body.getHeight()+" alpha="+body.getAlpha()+" scroll="+scroll.getHeight()+" y="+scroll.getScrollY()+" child="+ok.getWidth()+"x"+ok.getHeight();Bundle status=new Bundle();status.putString("stream",line+"\n");sendStatus(0,status);try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"paint-probe-"+reduce+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
 }
 result.putString("stream","RENDER PROBE COMPLETE\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}}
}
