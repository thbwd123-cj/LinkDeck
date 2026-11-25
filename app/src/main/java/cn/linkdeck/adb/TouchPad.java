package cn.linkdeck.adb;
import android.content.Context;import android.graphics.*;import android.os.*;import android.view.*;import android.view.accessibility.AccessibilityNodeInfo;import java.util.function.*;
/** 触摸板：滑动发方向键，轻点确定，长按菜单。手势判定见 TouchPadLogic；按键一律经回调发给 remoteKey，ADB 与红外模式通用。 */
public final class TouchPad extends View {
  private final Paint p=new Paint(3);
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final IntConsumer key;
  private final float thresholdPx,tapMaxPx;
  private float downX,downY,lastX,lastY,accX,accY;
  private long downAt,lastSentAt;
  private boolean pressed,longFired;
  private final Runnable longPress=new Runnable(){public void run(){if(!pressed||longFired)return;float moved=(float)Math.hypot(lastX-downX,lastY-downY);if(TouchPadLogic.isLongPress(System.currentTimeMillis()-downAt,moved,tapMaxPx)){longFired=true;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);key.accept(82);}}};
  public TouchPad(Context c,IntConsumer key){
    super(c);this.key=key;
    float d=getResources().getDisplayMetrics().density;
    thresholdPx=TouchPadLogic.SWIPE_THRESHOLD_DP*d;tapMaxPx=TouchPadLogic.TAP_MAX_DP*d;
    setFocusable(true);setClickable(true);
    setContentDescription("触摸板：滑动移动焦点，轻点确定，长按菜单。");
    setBackground(new DepthSurface(d,50,false));
    setMinimumHeight(dp(230));
  }
  private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
  protected void onDraw(Canvas c){
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(AuroraStyle.alpha(AuroraStyle.edge(),70));
    c.drawLine(getWidth()/2f,dp(14),getWidth()/2f,getHeight()-dp(14),p);
    c.drawLine(dp(14),getHeight()/2f,getWidth()-dp(14),getHeight()/2f,p);
    p.setStyle(Paint.Style.FILL);p.setColor(AuroraStyle.alpha(AuroraStyle.edge(),110));
    c.drawCircle(getWidth()/2f,getHeight()/2f,dp(5),p);
    p.setStyle(Paint.Style.STROKE);p.setColor(AuroraStyle.alpha(AuroraStyle.glowStart(),140));
    c.drawCircle(getWidth()/2f,getHeight()/2f,dp(16),p);
  }
  public boolean onTouchEvent(MotionEvent e){
    if(!isEnabled())return false;
    int action=e.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){
      pressed=true;longFired=false;
      downX=lastX=e.getX();downY=lastY=e.getY();accX=0;accY=0;
      downAt=System.currentTimeMillis();lastSentAt=0;
      getParent().requestDisallowInterceptTouchEvent(true);
      handler.postDelayed(longPress,TouchPadLogic.LONG_PRESS_MS);
      return true;
    }
    if(action==MotionEvent.ACTION_MOVE&&pressed){
      float x=e.getX(),y=e.getY();
      accX+=x-lastX;accY+=y-lastY;lastX=x;lastY=y;
      if((float)Math.hypot(lastX-downX,lastY-downY)>=tapMaxPx)handler.removeCallbacks(longPress);
      int code=TouchPadLogic.direction(accX,accY,thresholdPx);
      long now=System.currentTimeMillis();
      if(code!=0&&now-lastSentAt>=TouchPadLogic.REPEAT_MIN_MS){
        float[] rest=TouchPadLogic.consume(accX,accY,thresholdPx);
        accX=rest[0];accY=rest[1];lastSentAt=now;
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        key.accept(code);
      }
      return true;
    }
    if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
      handler.removeCallbacks(longPress);
      boolean was=pressed;pressed=false;
      if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
      if(was&&action==MotionEvent.ACTION_UP&&!longFired){
        float moved=(float)Math.hypot(lastX-downX,lastY-downY);
        if(TouchPadLogic.isTap(System.currentTimeMillis()-downAt,moved,tapMaxPx)){
          performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
          key.accept(23);
        }
        performClick();
      }
      return true;
    }
    return true;
  }
  protected void onDetachedFromWindow(){handler.removeCallbacks(longPress);pressed=false;super.onDetachedFromWindow();}
  public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus){handler.removeCallbacks(longPress);pressed=false;}}
  public boolean performClick(){super.performClick();return true;}
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
    super.onInitializeAccessibilityNodeInfo(info);
    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
  }
  public boolean performAccessibilityAction(int action,Bundle args){
    if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){
      if(!isEnabled())return false;
      key.accept(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?20:19);
      return true;
    }
    return super.performAccessibilityAction(action,args);
  }
}
