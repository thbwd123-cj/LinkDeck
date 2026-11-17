package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;import java.util.function.BooleanSupplier;
/** Decorative only. The existing status text remains the accessible source of truth. */
public final class ConnectionLamp extends Drawable {
 private final Paint paint=new Paint(3);private final BooleanSupplier connected,busy;private final int size;
 public ConnectionLamp(float density,BooleanSupplier connected,BooleanSupplier busy){size=Math.round(24*density);this.connected=connected;this.busy=busy;}
 public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
 public void draw(Canvas c){Rect b=getBounds();float x=b.exactCenterX(),y=b.exactCenterY(),r=Math.min(b.width(),b.height())*.16f;int color=busy.getAsBoolean()?0xffd8aa53:connected.getAsBoolean()?0xff48e0aa:0xffd8aa53;if(AuroraStyle.light())color=connected.getAsBoolean()?0xff007752:0xff90620d;
  for(int i=5;i>=1;i--){paint.setColor(AuroraStyle.alpha(color,9));c.drawCircle(x,y,r*(1+i*.42f),paint);}paint.setColor(color);c.drawCircle(x,y,r,paint);paint.setColor(AuroraStyle.alpha(0xffffffff,190));c.drawCircle(x-r*.2f,y-r*.3f,r*.22f,paint);
 }
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
