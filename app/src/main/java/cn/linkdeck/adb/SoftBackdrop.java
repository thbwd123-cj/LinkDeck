package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;import android.os.SystemClock;
/** Two cached, software-blurred light textures. Screen blending happens above the opaque base. */
public final class SoftBackdrop extends Drawable {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final PorterDuffXfermode screen=new PorterDuffXfermode(PorterDuff.Mode.SCREEN);
 private final RectF lightBounds=new RectF();private final float density;
 private Bitmap green,violet,blurred;private Canvas smallCanvas;private int[] pixels,work;private float phase,sampledPhase=-1;private long sampled=-1;
 public SoftBackdrop(){this(1);}
 public SoftBackdrop(float density){this.density=density;}
 protected void onBoundsChange(Rect r){
  if(r.width()<=0||r.height()<=0)return;
  if(green==null)green=light(AuroraStyle.GREEN_DIAMETER,AuroraStyle.glowStart(),AuroraStyle.GREEN_ALPHA);
  if(violet==null)violet=light(AuroraStyle.VIOLET_DIAMETER,AuroraStyle.glowEnd(),AuroraStyle.VIOLET_ALPHA);
  int w=Math.max(1,Math.min(240,Math.round(r.width()/(4*density)))),h=Math.max(1,Math.min(480,Math.round(w*(float)r.height()/r.width())));
  blurred=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);smallCanvas=new Canvas(blurred);pixels=new int[w*h];work=new int[w*h];sampled=-1;
 }
 private Bitmap light(float diameter,int color,float opacity){
  // CSS pixels map to dp. A quarter-resolution software canvas applies the 46 dp blur once.
  float extent=diameter+4*AuroraStyle.SPOT_BLUR;int size=Math.max(1,Math.round(extent/4));
  Bitmap texture=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(texture);c.scale(size/extent,size/extent);
  Paint soft=new Paint(3);soft.setColor(AuroraStyle.alpha(color,Math.round(255*opacity)));soft.setMaskFilter(new BlurMaskFilter(AuroraStyle.SPOT_BLUR,BlurMaskFilter.Blur.NORMAL));c.drawCircle(extent/2,extent/2,diameter/2,soft);return texture;
 }
 public void phase(float seconds){phase=seconds;invalidateSelf();}
 private void scene(Canvas c){
  Rect b=getBounds();c.drawColor(AuroraStyle.base());if(green==null||violet==null)return;
  float g=AuroraStyle.greenDrift(phase),v=AuroraStyle.violetDrift(phase);
  paint.setXfermode(screen);
  spot(c,green,b.left+b.width()*(.12f+.06f*g),b.top+b.height()*(.14f+.025f*g),AuroraStyle.GREEN_DIAMETER);
  spot(c,violet,b.left+b.width()*(.87f-.055f*v),b.top+b.height()*(.47f+.035f*v),AuroraStyle.VIOLET_DIAMETER);
  paint.setXfermode(null);
 }
 private void spot(Canvas c,Bitmap texture,float x,float y,float diameter){float extent=(diameter+4*AuroraStyle.SPOT_BLUR)*density;lightBounds.set(x-extent/2,y-extent/2,x+extent/2,y+extent/2);c.drawBitmap(texture,null,lightBounds,paint);}
 public void draw(Canvas c){scene(c);}
 public void drawBlurred(Canvas c){
  if(blurred==null)return;long now=SystemClock.uptimeMillis();
  if(sampled<0||(sampledPhase!=phase&&now-sampled>=125)){
   smallCanvas.save();smallCanvas.scale(blurred.getWidth()/(float)getBounds().width(),blurred.getHeight()/(float)getBounds().height());scene(smallCanvas);smallCanvas.restore();
   int w=blurred.getWidth(),h=blurred.getHeight();blurred.getPixels(pixels,0,w,0,0,w,h);
   int radius=Math.max(1,Math.round(AuroraStyle.GLASS_BLUR*density*w/getBounds().width()));
   AuroraBlur.apply(pixels,work,w,h,radius);AuroraBlur.apply(pixels,work,w,h,radius);
   blurred.setPixels(pixels,0,w,0,0,w,h);sampled=now;sampledPhase=phase;
  }
  paint.setXfermode(null);c.drawBitmap(blurred,null,getBounds(),paint);
 }
 public void release(){green=null;violet=null;blurred=null;smallCanvas=null;pixels=null;work=null;sampled=-1;}
 public void restore(){if(blurred==null)onBoundsChange(getBounds());}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
}
