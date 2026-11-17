package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Raised physical disc. Native children own every hit region and accessibility action. */
public final class RemoteFace extends Drawable {
 private final Paint p=new Paint(3);private float x,y,r;private Shader rim,face,halo;private boolean pressed;
 protected void onBoundsChange(Rect b){x=b.exactCenterX();y=b.exactCenterY()-b.height()*.012f;r=Math.min(b.width(),b.height())*.465f;if(r<=0)return;
  int base=AuroraStyle.surface();rim=new LinearGradient(x,y-r,x,y+r,new int[]{AuroraStyle.mix(base,AuroraStyle.edge(),.37f),AuroraStyle.mix(base,AuroraStyle.edge(),.10f),AuroraStyle.base()},new float[]{0,.27f,1},Shader.TileMode.CLAMP);
  face=new RadialGradient(x-r*.32f,y-r*.44f,r*1.1f,new int[]{0x38ffffff,0x0fffffff,AuroraStyle.light()?0x1f000000:0x57000000},new float[]{0,.42f,.88f},Shader.TileMode.CLAMP);
  halo=new RadialGradient(x,y,r*1.08f,new int[]{AuroraStyle.alpha(AuroraStyle.glowStart(),0),AuroraStyle.alpha(AuroraStyle.glowStart(),0),AuroraStyle.alpha(AuroraStyle.glowStart(),160),AuroraStyle.alpha(AuroraStyle.glowStart(),0)},new float[]{0,.82f,.93f,1},Shader.TileMode.CLAMP);
 }
 public boolean isStateful(){return true;}
 protected boolean onStateChange(int[] s){boolean down=false;for(int v:s)if(v==android.R.attr.state_pressed)down=true;if(down==pressed)return false;pressed=down;invalidateSelf();return true;}
 public void draw(Canvas c){if(r<=0)return;p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(AuroraStyle.alpha(0xff000000,AuroraStyle.light()?28:120));c.drawCircle(x,y+r*.065f,r*1.01f,p);
  p.setColor(AuroraStyle.mix(AuroraStyle.base(),0xff000000,AuroraStyle.light()?0:.35f));c.drawCircle(x,y+r*.038f,r,p);
  c.save();if(pressed)c.translate(0,r*.014f);p.setShader(rim);c.drawCircle(x,y,r,p);p.setShader(null);p.setColor(AuroraStyle.base());c.drawCircle(x,y,r*.91f,p);p.setShader(face);c.drawCircle(x,y,r*.89f,p);
  p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r*.006f));p.setColor(AuroraStyle.alpha(AuroraStyle.edge(),65));c.drawCircle(x,y,r*.985f,p);p.setColor(AuroraStyle.alpha(AuroraStyle.edge(),32));c.drawCircle(x,y,r*.88f,p);
  p.setStyle(Paint.Style.FILL);p.setShader(halo);p.setAlpha(pressed?255:210);c.drawCircle(x,y,r*1.08f,p);p.setAlpha(255);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r*.009f));p.setColor(AuroraStyle.alpha(AuroraStyle.glowStart(),128));c.drawCircle(x,y,r*.985f,p);p.setStyle(Paint.Style.FILL);c.restore();
 }
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
