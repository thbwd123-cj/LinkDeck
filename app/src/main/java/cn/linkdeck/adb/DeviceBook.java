package cn.linkdeck.adb;
import org.json.*;
import java.util.*;
import java.io.*;

/** Endpoint identity is host + port; user aliases survive successful reconnections. */
public final class DeviceBook {
  public static JSONArray list(String raw) {
    try { return new JSONArray(raw); } catch(Exception e) { return new JSONArray(); }
  }
  public interface Reset {boolean clear();}
  public static final class ReadResult {public final JSONArray entries;public final boolean recovered;ReadResult(JSONArray entries,boolean recovered){this.entries=entries;this.recovered=recovered;}}
  /** Devices are stored as a preference value, so preserve the damaged raw JSON in a separate backup before clearing. */
  public static ReadResult read(String raw,File directory,Reset reset)throws IOException {
    try{return new ReadResult(new JSONArray(raw),false);}
    catch(JSONException invalid){
      File backup=new File(directory,"devices-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".bak");
      try(FileOutputStream out=new FileOutputStream(backup)){out.write(raw.getBytes("UTF-8"));out.getFD().sync();}
      if(!reset.clear())throw new IOException("设备列表无法重置，已保留备份");
      return new ReadResult(new JSONArray(),true);
    }
  }
  public static JSONObject find(JSONArray a,String host,String port) {
    for(int i=0;i<a.length();i++) {
      JSONObject o=a.optJSONObject(i);
      if(o!=null && host.equals(o.optString("host")) && port.equals(o.optString("port"))) return o;
    }
    return null;
  }
  public static JSONArray save(JSONArray a,String h,String p,String model,String alias,boolean verified) throws JSONException {
    JSONObject o=find(a,h,p);
    if(o==null) o=new JSONObject();
    o.put("host",h).put("port",p);
    if(model!=null) o.put("name",model);
    if(alias!=null) o.put("alias",alias);
    if(verified) o.put("last",System.currentTimeMillis());
    JSONArray b=remove(a,h,p); b.put(o); return sorted(b);
  }
  public static JSONArray remove(JSONArray a,String h,String p) {
    JSONArray b=new JSONArray();
    for(int i=0;i<a.length();i++) { JSONObject o=a.optJSONObject(i); if(o!=null && !(h.equals(o.optString("host")) && p.equals(o.optString("port")))) b.put(o); }
    return b;
  }
  public static JSONArray sorted(JSONArray a) {
    List<JSONObject> items=new ArrayList<>();
    for(int i=0;i<a.length();i++) if(a.optJSONObject(i)!=null) items.add(a.optJSONObject(i));
    Collections.sort(items,(x,y)-> {
      int pin=Boolean.compare(y.optBoolean("pinned"),x.optBoolean("pinned"));
      return pin!=0?pin:Long.compare(y.optLong("last"),x.optLong("last"));
    });
    JSONArray b=new JSONArray();for(JSONObject o:items) b.put(o);return b;
  }
  public static String label(JSONObject o) {
    String alias=o.optString("alias").trim();
    return alias.isEmpty()?o.optString("name",o.optString("host")):alias;
  }
}
