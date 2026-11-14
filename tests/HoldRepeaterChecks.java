package cn.linkdeck.adb;
import java.util.*;
public final class HoldRepeaterChecks {
 static class Clock implements HoldRepeater.Schedule {long time,next;Runnable pending;public void post(Runnable r,long delay){pending=r;next=time+delay;}public void remove(Runnable r){if(pending==r)pending=null;}void until(long t){while(pending!=null&&next<=t){time=next;Runnable r=pending;pending=null;r.run();}time=t;}}
 public static void main(String[] args){
  Clock clock=new Clock();List<Long> sent=new ArrayList<>();boolean[] enabled={true};HoldRepeater r=new HoldRepeater(clock,()->enabled[0],()->sent.add(clock.time),180);
  r.start();clock.until(2000);if(sent.size()!=12||sent.get(1)!=180||sent.get(11)!=1980)throw new AssertionError("wrong interval "+sent);
  r.stop();clock.until(4000);if(sent.size()!=12)throw new AssertionError("action after release");
  r.start();enabled[0]=false;clock.until(4500);if(sent.size()!=13||clock.pending!=null)throw new AssertionError("action after focus lost");
  System.out.println("HOLD REPEATER PASSED: 180 ms cadence, release and focus loss stop");
 }
}
