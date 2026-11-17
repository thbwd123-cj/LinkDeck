package cn.linkdeck.adb;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Native pressed/focused states illuminate the ring without changing touch or hold handling. */
public final class RemoteKeyArt extends Drawable {
 private final Paint p=new Paint(3);private final int direction;private final Path path=new Path();private Shader core,glow;private float x,y,r;private boolean pressed,focused;
 public RemoteKeyArt(int direction){this.direction=direction;}
 protected void onBoundsChange(Rect b){x=b.exactCenterX();y=b.exactCenterY();r=Math.min(b.width(),b.height())*.45f;int base=AuroraStyle.surface();glow=AuroraGlow.gradient(b.left,b.top,b.right,b.bottom);core=r>0?new LinearGradient(x-r,y-r,x+r,y+r,new int[]{AuroraStyle.mix(base,AuroraStyle.edge(),.32f),AuroraStyle.mix(base,AuroraStyle.edge(),.06f),AuroraStyle.base()},new float[]{0,.36f,1},Shader.TileMode.CLAMP):null;}
 public boolean isStateful(){return true;}
 protected boolean onStateChange(int[] state){boolean down=false,focus=false;for(int s:state){if(s==android.R.attr.state_pressed)down=true;if(s==android.R.attr.state_focused)focus=true;}if(pressed==down&&focused==focus)return false;pressed=down;focused=focus;invalidateSelf();return true;}
 public void draw(Canvas c){if(r<=0)return;c.save();if(pressed)c.translate(0,r*.075f);
  if(direction<0){p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(AuroraStyle.alpha(0xff000000,AuroraStyle.light()?32:145));c.drawCircle(x,y+r*.065f,r,p);p.setShader(core);p.setAlpha(255);c.drawCircle(x,y,r*.96f,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.025f);p.setColor(AuroraStyle.alpha(AuroraStyle.edge(),110));c.drawCircle(x,y,r*.94f,p);p.setStrokeWidth(r*.012f);p.setColor(AuroraStyle.alpha(AuroraStyle.accent(),70));c.drawCircle(x,y,r*.84f,p);
  }else{c.save();c.translate(x,y);c.rotate(new int[]{0,180,270,90}[direction]);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setColor(UiPalette.color(0xffeff5ff));p.setStrokeWidth(r*.09f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);path.reset();path.moveTo(-r*.24f,r*.08f);path.lineTo(0,-r*.16f);path.lineTo(r*.24f,r*.08f);c.drawPath(path,p);c.restore();}
  if(pressed||focused)AuroraGlow.circle(c,p,glow,x,y,r*.84f,Math.max(1,r*.035f));p.setStyle(Paint.Style.FILL);c.restore();
 }
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
