package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import cn.linkdeck.adb.*;import java.io.*;import java.lang.reflect.*;import org.json.*;
/** Explicit synthetic transport tests for incomplete command UI. No TV connection. */
public class IncompleteConsoleChecks extends ConsoleChecks {
 int code(String v){return java.nio.ByteBuffer.wrap(v.getBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();}
 int[] packet(DataInputStream in)throws Exception{byte[] b=new byte[24];in.readFully(b);java.nio.ByteBuffer h=java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);int[] v=new int[6];for(int i=0;i<6;i++)v[i]=h.getInt();byte[] body=new byte[v[3]];in.readFully(body);return v;}
 void send(OutputStream out,String command,int a,int b,byte[] data)throws Exception{int cmd=code(command),sum=0;for(byte v:data)sum+=v&255;out.write(java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(cmd).putInt(a).putInt(b).putInt(data.length).putInt(sum).putInt(cmd^0xffffffff).array());out.write(data);out.flush();}
 public void onStart(){Bundle result=new Bundle();try {
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).commit();
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(1000);
  java.net.ServerSocket server=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));
  Thread peer=new Thread(()->{try(java.net.Socket socket=server.accept()){
   DataInputStream in=new DataInputStream(socket.getInputStream());OutputStream out=socket.getOutputStream();packet(in);send(out,"CNXN",0x1000000,4096,"device::\0".getBytes("UTF-8"));
   while(true){int[] header=packet(in);if(header[0]==code("OPEN")){int cid=header[1];send(out,"OKAY",cid+100,cid,new byte[0]);send(out,"WRTE",cid+100,cid,"  模拟电视返回文本  \n".getBytes("UTF-8"));send(out,"CLSE",cid+100,cid,new byte[0]);}}
  }catch(Exception ignored){}});peer.setDaemon(true);peer.start();
  AdbClient stub=new AdbClient();stub.connect("127.0.0.1",server.getLocalPort(),new AdbIdentity(new File(getTargetContext().getCacheDir(),"qa092-keys")));
  set("adb",stub);set("device","界面测试（模拟连接，非电视）");
  call("command",new Class[]{String.class,String.class,Runnable.class},"超时界面测试","echo fixture",null);
  waitFor("incomplete command opens dedicated result dialog",()->dialogHas("结果未确认")&&field("commandResultDialog")!=null&&!(Boolean)field("busy"));
  waitFor("footer does not display a fabricated exit code",()->!dialogHas("退出码 -1"));
  waitFor("partial raw text is preserved including whitespace",()->resultField("output").equals("  模拟电视返回文本  \n"));
  waitFor("incomplete command has no invented exit code",()->(Integer)resultField("exitCode")==-1&&document().contains("无有效退出码")&&document().contains("不代表完整结果"));
  File saved=new File(getTargetContext().getFilesDir(),"last-command.json");JSONObject j=new JSONObject(read(saved));
  waitFor("partial output and reason persisted",()->j.getString("output").equals(resultField("output"))&&!j.getString("failureReason").isEmpty());
  String doc=document();File export=new File(getTargetContext().getCacheDir(),"qa-incomplete092.txt");set("pendingCommandExport",doc);
  call("onActivityResult",new Class[]{int.class,int.class,Intent.class},105,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(export)));
  waitFor("partial result exports exact UTF-8 with uncertainty",()->export.exists()&&read(export).equals(doc));
  shot("v092-incomplete-result");resultTap("关闭");stub.close();call("previousCommand",new Class[]{});
  waitFor("last incomplete result can reopen while disconnected",()->dialogHas("结果未确认")&&dialogHas("模拟电视返回文本"));resultTap("关闭");
  j.remove("failureReason");try(FileOutputStream out=new FileOutputStream(saved)){out.write(j.toString().getBytes("UTF-8"));}
  call("previousCommand",new Class[]{});waitFor("older saved records without new field remain readable",()->field("commandResultDialog")!=null&&document().contains("无有效退出码"));
  server.close();export.delete();result.putString("stream","INCOMPLETE CONSOLE UI PASSED: "+passes+" checks.\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}}
}
