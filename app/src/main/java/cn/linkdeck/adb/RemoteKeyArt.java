package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Vector chevrons and a luminous tactile OK core, independent of font glyphs. */
public final class RemoteKeyArt extends Drawable {
 private final Paint p=new Paint(3);private final int direction;private final Path path=new Path();private Shader core;private float x,y,r;
 public RemoteKeyArt(int direction){this.direction=direction;}
 protected void onBoundsChange(Rect b){x=b.exactCenterX();y=b.exactCenterY();r=Math.min(b.width(),b.height())*.48f;core=r>0?new LinearGradient(x,y-r,x,y+r,new int[]{UiPalette.color(0xff65dbce),UiPalette.color(0xff164b54),UiPalette.color(0xff062029)},new float[]{0,.26f,1},Shader.TileMode.CLAMP):null;}
 public void draw(Canvas c){if(r<=0)return;if(direction<0){p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(UiPalette.color(0xff030e16));c.drawCircle(x,y,r,p);p.setShader(core);c.drawCircle(x,y,r*.94f,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setColor(UiPalette.color(0xff58e7d3));p.setStrokeWidth(r*.04f);c.drawCircle(x,y,r*.91f,p);p.setColor(UiPalette.color(0xff113f48));p.setStrokeWidth(r*.015f);c.drawCircle(x,y,r*.84f,p);}else{c.save();c.translate(x,y);c.rotate(new int[]{0,180,270,90}[direction]);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setColor(UiPalette.color(0xffeff5ff));p.setStrokeWidth(r*.09f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);path.reset();path.moveTo(-r*.24f,r*.08f);path.lineTo(0,-r*.16f);path.lineTo(r*.24f,r*.08f);c.drawPath(path,p);c.restore();}p.setStyle(Paint.Style.FILL);}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
