package cn.linkdeck.adb;
import java.util.function.BooleanSupplier;
/** Bounded hold repeat: one immediate action, then one every interval until released. */
public final class HoldRepeater {
 public interface Schedule {void post(Runnable r,long delay);void remove(Runnable r);}
 private final Schedule schedule;private final BooleanSupplier allowed;private final Runnable action;private final long interval;private boolean active;
 private final Runnable tick=new Runnable(){public void run(){if(!active)return;if(!allowed.getAsBoolean()){stop();return;}action.run();if(active)schedule.post(this,interval);}};
 public HoldRepeater(Schedule schedule,BooleanSupplier allowed,Runnable action,long interval){this.schedule=schedule;this.allowed=allowed;this.action=action;this.interval=interval;}
 public void start(){stop();active=true;action.run();if(active)schedule.post(tick,interval);}
 public void stop(){active=false;schedule.remove(tick);}
}
