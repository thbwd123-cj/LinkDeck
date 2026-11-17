package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Quiet glass-like key face with an emissive edge, never a large luminous color slab. */
public final class DepthSurface extends Drawable {
 private final Paint paint=new Paint(3);private final RectF face=new RectF(),shadow=new RectF();private final float density,radius;private final boolean emphasized;private Shader fill,edge,glow;private int alpha=255;private boolean pressed,focused;
 public DepthSurface(float density,float radius,boolean mint){this(density,radius,mint,false);}
 public DepthSurface(float density,float radius,boolean mint,boolean teal){this.density=density;this.radius=radius*density;emphasized=mint||teal;}
 protected void onBoundsChange(Rect b){
  face.set(b.left+2*density,b.top+2*density,b.right-2*density,b.bottom-4*density);shadow.set(face);shadow.offset(0,3*density);
  int base=AuroraStyle.surface();fill=new LinearGradient(0,b.top,0,b.bottom,new int[]{AuroraStyle.mix(base,AuroraStyle.light()?0xffffffff:AuroraStyle.edge(),AuroraStyle.light()?.6f:.075f),base,AuroraStyle.mix(base,AuroraStyle.base(),.4f)},null,Shader.TileMode.CLAMP);
  glow=AuroraGlow.gradient(b.left,b.top,b.right,b.bottom);
  edge=new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{AuroraStyle.alpha(emphasized?AuroraStyle.accent():AuroraStyle.edge(),emphasized?150:100),AuroraStyle.alpha(AuroraStyle.edge(),22),AuroraStyle.alpha(AuroraStyle.secondary(),55)},null,Shader.TileMode.CLAMP);
 }
 public boolean isStateful(){return true;}
 protected boolean onStateChange(int[] state){boolean down=false,focus=false;for(int s:state){if(s==android.R.attr.state_pressed)down=true;if(s==android.R.attr.state_focused)focus=true;}if(down==pressed&&focus==focused)return false;pressed=down;focused=focus;invalidateSelf();return true;}
 public void draw(Canvas c){
  if(fill==null)return;paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(AuroraStyle.alpha(0xff000000,AuroraStyle.light()?20:75));c.drawRoundRect(shadow,radius,radius,paint);
  c.save();if(pressed)c.translate(0,1.5f*density);paint.setShader(fill);paint.setAlpha(alpha);c.drawRoundRect(face,radius,radius,paint);paint.setStyle(Paint.Style.STROKE);paint.setShader(edge);paint.setStrokeWidth(density);c.drawRoundRect(face,radius,radius,paint);paint.setShader(null);
  if(emphasized||pressed||focused)AuroraGlow.round(c,paint,glow,face,radius,density);
  paint.setStyle(Paint.Style.FILL);c.restore();
 }
 public void getOutline(Outline out){out.setRoundRect(getBounds(),radius);}
 public void setAlpha(int a){alpha=a;invalidateSelf();}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
