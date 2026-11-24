package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;import android.view.View;
/** Crops the blurred aurora in window coordinates; native text and controls stay sharp. */
public final class GlassSurface extends Drawable {
 private final View owner;private final AuroraLayout root;private final float radius,density;private final boolean highlighted;
 private final Paint paint=new Paint(3);private final RectF rect=new RectF();private final Path clip=new Path();
 private final int[] location=new int[2],origin=new int[2];private Shader fill,edge,glow,topLine;private int alpha=255;
 public GlassSurface(View owner,AuroraLayout root,float radius,boolean highlighted){this.owner=owner;this.root=root;density=owner.getResources().getDisplayMetrics().density;this.radius=radius*density;this.highlighted=highlighted;root.register(this);}
 protected void onBoundsChange(Rect b){
  rect.set(b.left+density,b.top+density,b.right-density,b.bottom-density);clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);
  fill=AuroraStyle.light()?new LinearGradient(0,b.top,0,b.bottom,new int[]{AuroraStyle.alpha(AuroraStyle.glassTint(),AuroraStyle.glassTopAlpha()),AuroraStyle.alpha(AuroraStyle.glassTint(),AuroraStyle.glassBottomAlpha())},null,Shader.TileMode.CLAMP):new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{AuroraStyle.GLASS_SHEEN_TOP,AuroraStyle.GLASS_SHEEN_MID,AuroraStyle.GLASS_SHEEN_BOTTOM},new float[]{0,.55f,1},Shader.TileMode.CLAMP);
  edge=new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{0x29ffffff,0x29ffffff},null,Shader.TileMode.CLAMP);
  glow=AuroraGlow.frameGradient(b.left,b.top,b.right,b.bottom);
  topLine=new LinearGradient(b.left,b.top,b.right,b.top,new int[]{0x00ffffff,0x70ffffff,0x00ffffff},new float[]{0,.42f,1},Shader.TileMode.CLAMP);
 }
 void refresh(){if(owner.isAttachedToWindow()&&owner.isShown())invalidateSelf();}
 public void draw(Canvas c){
  if(fill==null)return;c.save();c.clipPath(clip);owner.getLocationInWindow(location);root.getLocationInWindow(origin);
  c.save();c.translate(origin[0]-location[0],origin[1]-location[1]);root.sky.drawBlurred(c);c.restore();
  paint.setStyle(Paint.Style.FILL);paint.setAlpha(alpha);paint.setShader(fill);c.drawRoundRect(rect,radius,radius,paint);c.restore();
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setShader(edge);c.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);
  paint.setShader(topLine);paint.setStrokeWidth(density);c.drawLine(rect.left+Math.min(radius,rect.width()/2),rect.top+density,rect.right-Math.min(radius,rect.width()/2),rect.top+density,paint);paint.setShader(null);
  if(highlighted)AuroraGlow.round(c,paint,glow,rect,radius,density);
  paint.setStyle(Paint.Style.FILL);
 }
 public void getOutline(Outline out){out.setRoundRect(getBounds(),radius);}
 public void setAlpha(int a){alpha=a;invalidateSelf();}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
