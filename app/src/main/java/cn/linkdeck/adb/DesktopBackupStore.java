package cn.linkdeck.adb;
import android.util.AtomicFile;
import java.io.*;
import org.json.*;

/** Atomic, app-private backups. A failed save aborts the remote modification. */
public final class DesktopBackupStore implements DesktopAssistant.Store {
  private final File directory;
  public DesktopBackupStore(File d){directory=d;}
  private AtomicFile file(String id)throws IOException {if(!id.matches("[0-9a-f]{64}"))throw new IOException("备份标识无效");if(!directory.exists()&&!directory.mkdirs())throw new IOException("无法创建备份目录");return new AtomicFile(new File(directory,"desktop_"+id+".json"));}
  public synchronized JSONObject load(String id)throws Exception {
    AtomicFile f=file(id);if(!f.getBaseFile().exists()&&!new File(f.getBaseFile()+".bak").exists())return null;
    try(InputStream in=f.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1024*1024)throw new IOException("备份文件异常过大");}return new JSONObject(out.toString("UTF-8"));}
  }
  public synchronized void save(String id,JSONObject value)throws Exception {
    if(!id.equals(value.optString("id")))throw new IOException("备份身份不匹配");byte[] bytes=value.toString().getBytes("UTF-8");if(bytes.length>1024*1024)throw new IOException("备份文件大小超限，未继续修改");AtomicFile f=file(id);FileOutputStream out=f.startWrite();
    try{out.write(bytes);f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}
  }
}
