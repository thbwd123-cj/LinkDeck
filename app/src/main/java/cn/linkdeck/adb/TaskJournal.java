package cn.linkdeck.adb;
import android.content.*;
import org.json.*;
import java.util.*;

/** Process-wide, bounded journal. Completed operations are never silently replayed. */
public final class TaskJournal {
  private static TaskJournal instance;
  private final SharedPreferences prefs;
  private JSONArray entries;
  private long revision;
  public synchronized long revision() {return revision;}
  public static synchronized TaskJournal get(Context c) {
    if(instance==null) instance=new TaskJournal(c.getApplicationContext().getSharedPreferences("tasks07",0));
    return instance;
  }
  TaskJournal(SharedPreferences p) {
    prefs=p; entries=DeviceBook.list(p.getString("entries","[]"));
    for(int i=0;i<entries.length();i++) {
      JSONObject o=entries.optJSONObject(i);
      if(o!=null && active(o)) try {o.put("state","interrupted").put("detail","上次进程结束前未收到完成状态，结果未知。请先检查电视实际状态，再决定是否重新操作。").put("ended",System.currentTimeMillis());}catch(Exception ignored){}
    }
    persist();
  }
  public static boolean active(JSONObject o) {String s=o.optString("state");return s.equals("queued")||s.equals("running");}
  public synchronized String enqueue(String name,String host,String port,String path) {
    String id=UUID.randomUUID().toString();
    JSONArray previous=entries;
    try {
      JSONObject o=new JSONObject().put("id",id).put("name",name).put("host",host).put("port",port).put("path",path).put("state","queued").put("created",System.currentTimeMillis());
      JSONArray a=new JSONArray();a.put(o);
      for(int i=0;i<entries.length();i++) if(i<79||active(entries.getJSONObject(i))) a.put(entries.get(i));
      if(!prefs.edit().putString("entries",a.toString()).commit()){prefs.edit().putString("entries",previous.toString()).commit();throw new IllegalStateException("任务记录无法保存");}
      entries=a;revision++;return id;
    }catch(Exception e){entries=previous;throw new IllegalStateException("任务登记失败，操作未执行",e);}
  }
  private JSONObject item(String id) {for(int i=0;i<entries.length();i++){JSONObject o=entries.optJSONObject(i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;}
  public synchronized void begin(String id) {try {JSONObject o=item(id);if(o!=null)o.put("state","running").put("started",System.currentTimeMillis());persist();}catch(Exception ignored){}}
  public synchronized void progress(String id,String message) {try {JSONObject o=item(id);if(o!=null&&active(o))o.put("progress",message);revision++;}catch(Exception ignored){}}
  public synchronized void finish(String id,boolean success,String detail) {
    try {JSONObject o=item(id);if(o!=null){String value=detail==null?"":detail;if(value.length()>16000)value=value.substring(0,16000)+"\n…任务摘要已截短；命令完整输出请从命令结果查看或导出。";o.put("state",success?"success":"failed").put("detail",value).put("ended",System.currentTimeMillis());}persist();}catch(Exception ignored){}
  }
  public synchronized void finishPartial(String id,String detail){finish(id,false,detail);try{JSONObject o=item(id);if(o!=null)o.put("state","partial");persist();}catch(Exception ignored){}}
  public synchronized void attachResult(String id,String filename){try{JSONObject o=item(id);if(o!=null)o.put("commandFile",filename);persist();}catch(Exception ignored){}}
  public synchronized JSONArray snapshot() {return DeviceBook.list(entries.toString());}
  public synchronized void clearFinished() {JSONArray a=new JSONArray();for(int i=0;i<entries.length();i++){JSONObject o=entries.optJSONObject(i);if(o!=null&&active(o))a.put(o);}entries=a;persist();}
  public synchronized void interrupt(Set<String> ids) {
    for(String id:ids) try {JSONObject o=item(id);if(o!=null&&active(o))o.put("state","interrupted").put("ended",System.currentTimeMillis()).put("detail","页面已退出，未收到命令完成状态，结果未知。请先核对电视实际状态。");}catch(Exception ignored){}
    persist();
  }
  private void persist() {revision++;prefs.edit().putString("entries",entries.toString()).apply();}
  public static String state(JSONObject o) {
    switch(o.optString("state")){case "queued":return "排队中";case "running":return "执行中";case "success":return "已完成";case "failed":return "失败";case "partial":return "部分完成";default:return "已中断 · 结果未知";}
  }
}
