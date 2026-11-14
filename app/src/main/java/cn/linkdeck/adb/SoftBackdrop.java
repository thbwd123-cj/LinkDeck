package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Static dark background: shaders are reused until bounds change. */
public final class SoftBackdrop extends Drawable {
 private final Paint paint=new Paint(3);private Shader top,side;
 protected void onBoundsChange(Rect r){if(r.width()>0){top=new RadialGradient(r.left+r.width()*.06f,r.top+r.height()*.04f,r.width()*.95f,new int[]{0x30225168,0x00225168},null,Shader.TileMode.CLAMP);side=new RadialGradient(r.right,r.top+r.height()*.5f,r.width()*.8f,new int[]{0x122e8a80,0x002e8a80},null,Shader.TileMode.CLAMP);}}
 public void draw(Canvas c){Rect r=getBounds();c.drawColor(0xff0b131e);if(top!=null){paint.setShader(top);c.drawRect(r,paint);paint.setShader(side);c.drawRect(r,paint);paint.setShader(null);}}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
}
