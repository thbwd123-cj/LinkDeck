package cn.linkdeck.adb;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;

/** Independently curated optional media apps; catalog loading always applies immutable core rules. */
public final class DebloatCatalog {
 public static final class Entry {
  public final String pkg,name,description;
  Entry(String p,String n,String d){pkg=p;name=n;description=d;}
 }
 private static final Pattern PACKAGE=Pattern.compile("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+");
 private static final Pattern CORE_WORD=Pattern.compile("(^|[._])(launcher|home|settings|setting|systemui|system|inputmethod|ime|keyboard|packageinstaller|permissioncontroller|installer|framework|setupwizard)([._]|$)");
 private static final Set<String> EXACT=new HashSet<>(Arrays.asList("android","app.orchard.tv","app.orchard.tvlite","app.orchard.tv.lite","app.orchard.guard","cn.linkdeck.adb","com.google.android.tvlauncher","com.google.android.apps.tv.launcherx","com.miui.tvlauncher","com.skyworthdigital.home","com.coocaa.tvpi"));
 private final Map<String,Entry> entries;
 private DebloatCatalog(Map<String,Entry> entries){this.entries=Collections.unmodifiableMap(entries);}
 public static boolean validPackage(String pkg){return pkg!=null&&pkg.length()<=255&&PACKAGE.matcher(pkg).matches();}
 public static boolean protectedPackage(String pkg){
  if(!validPackage(pkg))return true;
  String p=pkg.toLowerCase(Locale.ROOT);
  return EXACT.contains(p)||p.startsWith("android.")||p.startsWith("com.android.")||p.startsWith("com.google.android.")||p.startsWith("com.google.tv.")||p.startsWith("com.miui.system")||p.startsWith("com.miui.home")||p.contains("launcher")||p.contains("inputmethod")||p.contains("keyboard")||p.contains("packageinstaller")||p.contains("permissioncontroller")||p.contains("systemui")||CORE_WORD.matcher(p).find();
 }
 public static DebloatCatalog read(InputStream input)throws Exception {
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
  while((n=input.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>65536)throw new IOException("候选清单超出大小限制");}
  JSONObject root=new JSONObject(out.toString("UTF-8"));if(root.optInt("schema",-1)!=1)throw new IOException("候选清单版本不受支持");
  JSONArray array=root.getJSONArray("entries");if(array.length()==0||array.length()>200)throw new IOException("候选清单数量无效");
  Map<String,Entry> result=new LinkedHashMap<>();
  for(int i=0;i<array.length();i++){
   JSONObject o=array.getJSONObject(i);String pkg=o.getString("package").trim(),name=o.getString("name").trim(),description=o.getString("description").trim();
   if(protectedPackage(pkg))throw new IOException("候选清单包含受保护或无效的包："+pkg);
   if(name.length()>60||description.length()>180||!name.matches(".*[\\u4e00-\\u9fff].*")||description.isEmpty())throw new IOException("候选清单名称或说明无效："+pkg);
   if(result.put(pkg,new Entry(pkg,name,description))!=null)throw new IOException("候选清单包含重复包："+pkg);
  }
  return new DebloatCatalog(result);
 }
 public Entry get(String pkg){return entries.get(pkg);}
 public Collection<Entry> entries(){return entries.values();}
 public Entry allowed(String pkg)throws IOException{if(protectedPackage(pkg))throw new IOException("该应用属于受保护的核心组件，不能卸载");Entry e=get(pkg);if(e==null)throw new IOException("此应用不在受保护候选清单中，不能通过该流程卸载");return e;}
}
