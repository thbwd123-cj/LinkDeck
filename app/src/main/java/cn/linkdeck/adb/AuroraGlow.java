package cn.linkdeck.adb;
import android.graphics.*;
/** Shared green-to-violet edge emission. Gradients are cached by the owning drawable. */
final class AuroraGlow {
 static Shader gradient(float left,float top,float right,float bottom){return new LinearGradient(left,top,right==left?right+1:right,bottom,new int[]{AuroraStyle.alpha(AuroraStyle.glowStart(),128),AuroraStyle.alpha(AuroraStyle.glowEnd(),128)},null,Shader.TileMode.CLAMP);}
 static Shader frameGradient(float left,float top,float right,float bottom){return new LinearGradient(left,top,right==left?right+1:right,bottom,new int[]{AuroraStyle.alpha(AuroraStyle.frameStart(),128),AuroraStyle.alpha(AuroraStyle.frameEnd(),128)},null,Shader.TileMode.CLAMP);}
 static void round(Canvas c,Paint p,Shader gradient,RectF bounds,float radius,float unit){
  p.setShader(gradient);p.setStyle(Paint.Style.STROKE);
  for(int i=4;i>=1;i--){p.setStrokeWidth(unit*i*2.5f);p.setAlpha(i==1?255:i==2?90:40);c.drawRoundRect(bounds,radius,radius,p);}
  p.setAlpha(255);p.setShader(null);p.setStyle(Paint.Style.FILL);
 }
 static void circle(Canvas c,Paint p,Shader gradient,float x,float y,float radius,float unit){
  p.setShader(gradient);p.setStyle(Paint.Style.STROKE);
  for(int i=4;i>=1;i--){p.setStrokeWidth(unit*i*2.5f);p.setAlpha(i==1?255:i==2?90:40);c.drawCircle(x,y,radius,p);}
  p.setAlpha(255);p.setShader(null);p.setStyle(Paint.Style.FILL);
 }
 private AuroraGlow(){}
}
