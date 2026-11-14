package cn.linkdeck.adb;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Uses the same unbound socket route as manual ADB connection. */
public final class TcpCandidateProbe {
  private TcpCandidateProbe() {}
  public static boolean open(String host,int port,int timeoutMillis){
    if(host==null||host.isEmpty()||port<1||port>65535)return false;
    try(Socket socket=new Socket()){
      socket.connect(new InetSocketAddress(host,port),timeoutMillis);
      return true;
    }catch(Exception ignored){return false;}
  }
}
