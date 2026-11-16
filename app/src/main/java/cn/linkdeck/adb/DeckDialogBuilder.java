package cn.linkdeck.adb;
import android.app.AlertDialog;import android.content.Context;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.FrameLayout;
/** Shared native sheet treatment; does not replace caller click/dismiss listeners or confirmation logic. */
public final class DeckDialogBuilder extends AlertDialog.Builder {
 private final Context context;
 public DeckDialogBuilder(Context c){super(c);context=c;}
 private int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
 @Override public AlertDialog create(){
  AlertDialog d=super.create();Window w=d.getWindow();if(w!=null){w.setGravity(Gravity.BOTTOM);if(context.getSharedPreferences("linkdeck",Context.MODE_PRIVATE).getBoolean("reduce_motion",false)||!android.animation.ValueAnimator.areAnimatorsEnabled())w.setWindowAnimations(0);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
   View decor=w.getDecorView();decor.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{if(d.isShowing()){int desired=Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(20),dp(570));if(w.getAttributes().width!=desired)v.post(()->{if(d.isShowing())w.setLayout(desired,WindowManager.LayoutParams.WRAP_CONTENT);});}});decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewDetachedFromWindow(View v){}public void onViewAttachedToWindow(View v){v.post(()->{
    if(!d.isShowing())return;int titleId=context.getResources().getIdentifier("alertTitle","id","android");android.widget.TextView title=d.findViewById(titleId);if(title!=null){title.setTextSize(18);title.setTypeface(null,android.graphics.Typeface.BOLD);}int width=Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(20),dp(570));w.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);WindowManager.LayoutParams p=w.getAttributes();p.y=dp(10);p.dimAmount=.56f;w.setAttributes(p);
    if(context.getSharedPreferences("linkdeck",Context.MODE_PRIVATE).getBoolean("reduce_motion",false)||!android.animation.ValueAnimator.areAnimatorsEnabled())for(int which:new int[]{-1,-2,-3}){android.widget.Button action=d.getButton(which);if(action!=null)action.setStateListAnimator(null);}
    FrameLayout content=d.findViewById(android.R.id.content);if(content!=null&&content.findViewWithTag("deck-handle")==null){View handle=new View(context);handle.setTag("deck-handle");handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);GradientDrawable bg=new GradientDrawable();bg.setColor(UiPalette.color(0xff617187));bg.setCornerRadius(dp(3));handle.setBackground(bg);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(34),dp(4),Gravity.TOP|Gravity.CENTER_HORIZONTAL);hp.topMargin=dp(8);content.addView(handle,hp);}
   });}});
  }return d;
 }
 @Override public AlertDialog show(){AlertDialog d=create();d.show();return d;}
}
