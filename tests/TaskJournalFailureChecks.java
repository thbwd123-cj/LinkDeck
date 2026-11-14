package cn.linkdeck.adb;
import android.content.SharedPreferences;
import java.util.*;
public class TaskJournalFailureChecks {
 static class Pref implements SharedPreferences {
  Map<String,String> map=new HashMap<>();boolean fail;
  public String getString(String key,String fallback){return map.getOrDefault(key,fallback);}
  public Editor edit(){return new E();}
  class E implements Editor {Map<String,String> update=new HashMap<>();public Editor putString(String k,String v){update.put(k,v);return this;}public boolean commit(){if(fail)return false;map.putAll(update);return true;}public void apply(){map.putAll(update);}}
 }
 public static void main(String[] a)throws Exception{
  Pref p=new Pref();TaskJournal j=new TaskJournal(p);String ok=j.enqueue("first","h","5555","");
  if(ok==null||j.snapshot().length()!=1)throw new AssertionError("successful record missing");
  p.fail=true;try{j.enqueue("second","h","5555","");throw new AssertionError("failed write returned an ID");}catch(IllegalStateException expected){}
  if(j.snapshot().length()!=1||!p.map.get("entries").contains(ok))throw new AssertionError("failure mutated existing entries");
  p.fail=false;if(j.enqueue("third","h","5555","")==null||j.snapshot().length()!=2)throw new AssertionError("failed to recover");
  System.out.println("TASK JOURNAL PASSED: write failure is surfaced and existing records preserved");
 }
}
