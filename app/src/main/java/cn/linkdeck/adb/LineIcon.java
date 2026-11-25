package cn.linkdeck.adb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Resolution-independent outline icons drawn with Android Canvas. */
public final class LineIcon extends View {
  private final int kind;
  private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);

  public LineIcon(Context context, int kind, int color) {
    super(context);
    this.kind = kind;
    pen.setColor(color);
    pen.setStyle(Paint.Style.STROKE);
    pen.setStrokeWidth(1.65f);
    pen.setStrokeCap(Paint.Cap.ROUND);
    pen.setStrokeJoin(Paint.Join.ROUND);
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.save();
    c.scale(getWidth() / 24f, getHeight() / 24f);
    if (kind == 0) {
      c.drawRoundRect(new RectF(3, 4, 21, 17), 2, 2, pen);
      c.drawLine(9, 21, 15, 21, pen);
      c.drawLine(12, 17, 12, 21, pen);
    } else if (kind == 1) {
      c.drawRoundRect(new RectF(6, 2, 18, 22), 4, 4, pen);
      c.drawCircle(12, 8, 2.4f, pen);
      c.drawLine(9, 15, 15, 15, pen);
      c.drawLine(12, 12, 12, 18, pen);
    } else if (kind == 2) {
      for (int x : new int[] {3, 14})
        for (int y : new int[] {3, 14})
          c.drawRoundRect(new RectF(x, y, x + 7, y + 7), 1.6f, 1.6f, pen);
    } else if (kind == 3) {
      Path p = new Path();
      p.moveTo(3, 7);
      p.lineTo(3, 5);
      p.quadTo(3, 3, 5, 3);
      p.lineTo(10, 3);
      p.lineTo(12, 6);
      p.lineTo(19, 6);
      p.quadTo(21, 6, 21, 8);
      p.lineTo(21, 18);
      p.quadTo(21, 20, 19, 20);
      p.lineTo(5, 20);
      p.quadTo(3, 20, 3, 18);
      p.close();
      c.drawPath(p, pen);
      c.drawLine(3, 9, 21, 9, pen);
    } else if(kind==4){
      Path gear=new Path();
      for(int tooth=0;tooth<8;tooth++)for(int corner=0;corner<4;corner++){
        double angle=Math.toRadians(tooth*45+corner*11.25-90);
        float radius=(corner==1||corner==2)?9.5f:7.3f;
        float x=12+(float)Math.cos(angle)*radius,y=12+(float)Math.sin(angle)*radius;
        if(tooth==0&&corner==0)gear.moveTo(x,y);else gear.lineTo(x,y);
      }
      gear.close();c.drawPath(gear,pen);c.drawCircle(12,12,3.1f,pen);
    } else if(kind==5){
      Path back=new Path();back.moveTo(10,5);back.lineTo(4,11);back.lineTo(10,17);c.drawPath(back,pen);c.drawLine(4,11,15,11,pen);c.drawArc(new RectF(10,11,21,22),-90,145,false,pen);
    } else if(kind==6){
      Path home=new Path();home.moveTo(3,11);home.lineTo(12,3);home.lineTo(21,11);home.moveTo(6,9);home.lineTo(6,21);home.lineTo(18,21);home.lineTo(18,9);home.moveTo(10,21);home.lineTo(10,15);home.lineTo(14,15);home.lineTo(14,21);c.drawPath(home,pen);
    } else if(kind==7){
      for(int y:new int[]{5,12,19})c.drawLine(4,y,20,y,pen);
    } else if(kind==10){
      c.drawRoundRect(new RectF(2,5,22,19),2,2,pen);for(int y:new int[]{9,12})for(int x:new int[]{6,10,14,18})c.drawPoint(x,y,pen);c.drawLine(7,16,17,16,pen);
    } else if(kind==11){
      c.drawArc(new RectF(4,4,20,21),-55,290,false,pen);c.drawLine(12,2,12,11,pen);
    } else if(kind==12||kind==13){
      Path speaker=new Path();speaker.moveTo(3,9);speaker.lineTo(7,9);speaker.lineTo(12,5);speaker.lineTo(12,19);speaker.lineTo(7,15);speaker.lineTo(3,15);speaker.close();c.drawPath(speaker,pen);c.drawArc(new RectF(11,7,19,17),-65,130,false,pen);if(kind==13)c.drawArc(new RectF(9,3,23,21),-65,130,false,pen);
    } else if(kind==9){
      c.drawLine(12,3,12,16,pen);Path down=new Path();down.moveTo(7,11);down.lineTo(12,16);down.lineTo(17,11);c.drawPath(down,pen);Path tray=new Path();tray.moveTo(4,16);tray.lineTo(4,21);tray.lineTo(20,21);tray.lineTo(20,16);c.drawPath(tray,pen);
    } else if(kind==14){
      c.drawRoundRect(new RectF(9,3,15,13),3,3,pen);
      c.drawArc(new RectF(5,10,19,19),0,180,false,pen);
      c.drawLine(12,19,12,22,pen);
      c.drawLine(8,22,16,22,pen);
    } else {
      for (int i = 0; i < 3; i++) {
        float x = 5 + i * 7, y = i == 1 ? 8 : 15;
        c.drawLine(x, 3, x, y - 2, pen);
        c.drawLine(x, y + 2, x, 21, pen);
        c.drawCircle(x, y, 2, pen);
      }
    }
    c.restore();
  }
}
