package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import cn.linkdeck.adb.*;import java.io.*;import java.lang.reflect.*;import org.json.*;
public class AerialUiChecks extends RuntimeChecks {
 interface Test{void run()throws Exception;}
 void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);passes++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");sendStatus(0,b);}
 void reject(Test t,String msg)throws Exception{try{t.run();throw new AssertionError("accepted "+msg);}catch(IOException e){check(true,msg);}catch(Exception e){Throwable c=e;while(c.getCause()!=null)c=c.getCause();if(!(c instanceof IOException))throw e;check(true,msg);}}
 File asset(String name)throws Exception{File f=new File(getTargetContext().getCacheDir(),"qa08-"+System.nanoTime()+"-"+name);try(InputStream in=getContext().getAssets().open(name);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;}
 String read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 void put(Object o,String key,Object value)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
 Object plan(File file,AerialDownload.Asset source,int sdk)throws Exception {
  Class<?> c=Class.forName("cn.linkdeck.adb.MainActivity$AerialPlan");Constructor<?> ctor=c.getDeclaredConstructor();ctor.setAccessible(true);Object p=ctor.newInstance();
  put(p,"file",file);put(p,"asset",source);put(p,"pkg",AerialSupport.FULL);put(p,"variant","standard");put(p,"sdk",sdk);put(p,"abis","armeabi-v7a");put(p,"brand","coocaa");put(p,"model","测试电视（预览，未连接）");put(p,"installed",new AerialInstaller.Installed());put(p,"decision",-1);
  return p;
 }
 public void onStart(){Bundle result=new Bundle();try {
  File jsonFile=asset("aerial-release.json"),apk=asset("aerial-standard.apk");String json=read(jsonFile);
  AerialDownload.Asset source=AerialDownload.metadata(json,"standard");
  check(source.url.startsWith("https://github.com/pengchujin/aerial-tv/")&&source.digest.length()==64,"official release metadata resolves pinned APK and checksum");
  AerialDownload.verify(apk,source);check(true,"actual official APK matches release length and SHA-256");
  JSONObject bad=new JSONObject(json);JSONArray assets=bad.getJSONArray("assets");for(int i=0;i<assets.length();i++)if(assets.getJSONObject(i).optString("name").equals("aerial-standard.apk"))assets.getJSONObject(i).put("browser_download_url","https://evil.invalid/aerial-standard.apk");
  reject(()->AerialDownload.metadata(bad.toString(),"standard"),"non-official APK URL rejected");
  JSONObject draft=new JSONObject(json);draft.put("prerelease",true);reject(()->AerialDownload.metadata(draft.toString(),"standard"),"prerelease not offered as stable update");
  AerialDownload.Asset wrong=new AerialDownload.Asset();wrong.size=apk.length();wrong.digest="0000000000000000000000000000000000000000000000000000000000000000";reject(()->AerialDownload.verify(apk,wrong),"corrupted or mismatched checksum blocks installation");
  Method trusted=AerialDownload.class.getDeclaredMethod("trusted",java.net.URL.class);trusted.setAccessible(true);
  check(!(Boolean)trusted.invoke(null,new java.net.URL("http://aerial.qust.me/download/a.apk"))&&!(Boolean)trusted.invoke(null,new java.net.URL("https://github.com/other/repo/releases/a.apk")),"HTTP and unrelated repository redirects rejected");
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(1000);
  call("show",new Class[]{int.class},2);Thread.sleep(500);shot("v08-apps");tap("桌面管理  ›");dialogTap("检查更新");waitFor("offline Aerial action requests connection",()->dialogHas("连接后即可操作"));dialogTap("继续浏览");
  Object p=plan(apk,source,22);Class<?> c=p.getClass();call("validateAerialArchive",new Class[]{c},p);
  Field min=c.getDeclaredField("minimum");min.setAccessible(true);check(min.getInt(p)==21,"real official APK minimum supports Android 5.1");
  File second=asset("aerial-standard.apk");Object old=plan(second,source,20);reject(()->call("validateAerialArchive",new Class[]{c},old),"full official APK rejected for Android 4.x");
  Object wrongPackage=plan(new File(getTargetContext().getApplicationInfo().sourceDir),source,22);reject(()->call("validateAerialArchive",new Class[]{c},wrongPackage),"downloaded package must be actual Aerial");
  call("showAerialPlan",new Class[]{c},p);dialogTap("查看检查详情  ﹀");waitFor("prepared plan distinguishes install support and desktop takeover",()->dialogHas("安装到电视")&&dialogHas("不保证主页键"));shot("v08-aerial-plan");dialogTap("取消");waitFor("cancel clears prepared APK",()->!apk.exists());
  call("versionManager",new Class[]{});waitFor("version management offers history and rollback",()->dialogHas("历史版本与回退"));shot("v08-aerial-options");dialogTap("关闭");
  second.delete();jsonFile.delete();result.putString("stream","AERIAL NATIVE QA PASSED: "+passes+" checks.\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}}
}
