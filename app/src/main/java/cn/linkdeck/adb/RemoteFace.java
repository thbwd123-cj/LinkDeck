package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Cached static material shaders; accessible controls remain separate native views. */
public final class RemoteFace extends Drawable {
 private final Paint p=new Paint(3);private float x,y,r;private Shader rim,face,halo;
 protected void onBoundsChange(Rect b){x=b.exactCenterX();y=b.exactCenterY();r=Math.min(b.width(),b.height())*.475f;if(r<=0)return;rim=new LinearGradient(x,y-r,x,y+r,new int[]{0xff68778a,0xff2a3748,0xff0b111a},new float[]{0,.22f,1},Shader.TileMode.CLAMP);face=new LinearGradient(x-r,y-r,x+r,y+r,new int[]{0xff2b3543,0xff1b2532,0xff101c26},null,Shader.TileMode.CLAMP);halo=new RadialGradient(x,y,r*.49f,new int[]{0x4453edcf,0x1648cdb9,0x0048cdb9},new float[]{0,.62f,1},Shader.TileMode.CLAMP);}
 public void draw(Canvas c){if(r<=0)return;p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(0x60000000);c.drawCircle(x,y+r*.025f,r*1.02f,p);p.setAlpha(255);p.setShader(rim);c.drawCircle(x,y,r,p);p.setShader(null);p.setColor(0xff090f17);c.drawCircle(x,y,r*.9f,p);p.setShader(face);c.drawCircle(x,y,r*.894f,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r*.004f));p.setColor(0xff364553);c.drawCircle(x,y,r*.884f,p);p.setStyle(Paint.Style.FILL);p.setShader(halo);c.drawCircle(x,y,r*.49f,p);p.setShader(null);}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
