package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Static bevel, edge light and bottom seam. No bitmap or continuous redraw. */
public final class DepthSurface extends Drawable {
 private final Paint paint=new Paint(3);private final RectF face=new RectF(),rim=new RectF(),shadow=new RectF();private final float density,radius;private final boolean mint,teal;private Shader fill,edge;private int alpha=255;
 public DepthSurface(float density,float radius,boolean mint){this(density,radius,mint,false);}public DepthSurface(float density,float radius,boolean mint,boolean teal){this.density=density;this.radius=radius*density;this.mint=mint;this.teal=teal;}
 protected void onBoundsChange(Rect b){rim.set(b.left+density,b.top+density,b.right-density,b.bottom-2*density);shadow.set(rim.left,rim.top+3*density,rim.right,rim.bottom+2*density);face.set(rim);face.inset(density,density);fill=new LinearGradient(0,b.top,0,b.bottom,mint?new int[]{0xff8bffe9,0xff42dcc8,0xff15a89e}:teal?new int[]{0xff286663,0xff173d43,0xff102731}:new int[]{0xff344557,0xff1c2c3a,0xff0e1c28},new float[]{0,.35f,1},Shader.TileMode.CLAMP);edge=new LinearGradient(0,b.top,0,b.bottom,mint?new int[]{0xffc1fff2,0xff309d95,0xff07534e}:teal?new int[]{0xff5a9b97,0xff2a6968,0xff091c24}:new int[]{0xff70829a,0xff35485e,0xff060e16},null,Shader.TileMode.CLAMP);}
 public void draw(Canvas c){if(fill==null)return;paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(0xff040b13);paint.setAlpha(alpha);c.drawRoundRect(shadow,radius,radius,paint);paint.setShader(edge);paint.setAlpha(alpha);c.drawRoundRect(rim,radius,radius,paint);paint.setShader(fill);c.drawRoundRect(face,Math.max(0,radius-density),Math.max(0,radius-density),paint);paint.setShader(null);}
 public void getOutline(Outline out){out.setRoundRect(getBounds(),radius);}
 public void setAlpha(int a){alpha=a;invalidateSelf();}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
