package cn.linkdeck.adb;
import android.content.Context;import android.animation.ValueAnimator;import android.view.View;import android.view.animation.LinearInterpolator;import android.widget.LinearLayout;import android.os.SystemClock;import java.util.ArrayList;import java.lang.ref.WeakReference;import java.util.Iterator;import java.util.function.BooleanSupplier;
/** Decoration lifecycle; no animations when hidden, unfocused, detached, or motion disabled. */
public final class AuroraLayout extends LinearLayout {
 final SoftBackdrop sky;
 private final BooleanSupplier motion;private final ArrayList<WeakReference<GlassSurface>> glass=new ArrayList<>();private ValueAnimator animation;private long lastFrame;private float phase;
 public AuroraLayout(Context context,BooleanSupplier motion){super(context);this.motion=motion;sky=new SoftBackdrop(getResources().getDisplayMetrics().density);setOrientation(VERTICAL);setBackground(sky);}
 void register(GlassSurface surface){glass.add(new WeakReference<>(surface));}
 public void refreshMotion(){
  if(motion==null)return;
  boolean run=isAttachedToWindow()&&hasWindowFocus()&&getWindowVisibility()==View.VISIBLE&&isShown()&&motion.getAsBoolean();
  if(!run){if(animation!=null){animation.cancel();animation=null;}return;}
  if(animation!=null)return;
  sky.restore();animation=ValueAnimator.ofFloat(phase,phase+260);animation.setDuration(260000);animation.setRepeatCount(ValueAnimator.INFINITE);animation.setInterpolator(new LinearInterpolator());
  animation.addUpdateListener(a->{if(!motion.getAsBoolean()){refreshMotion();return;}long now=SystemClock.uptimeMillis();if(now-lastFrame<50)return;lastFrame=now;phase=(Float)a.getAnimatedValue();sky.phase(phase);for(Iterator<WeakReference<GlassSurface>> it=glass.iterator();it.hasNext();){GlassSurface surface=it.next().get();if(surface==null)it.remove();else surface.refresh();}});animation.start();
 }
 protected void onAttachedToWindow(){super.onAttachedToWindow();sky.restore();refreshMotion();}
 protected void onDetachedFromWindow(){if(animation!=null){animation.cancel();animation=null;}sky.release();super.onDetachedFromWindow();}
 public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);refreshMotion();}
 protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);refreshMotion();}
 protected void onVisibilityChanged(View view,int visibility){super.onVisibilityChanged(view,visibility);refreshMotion();}
}
