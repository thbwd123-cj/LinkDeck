package cn.linkdeck.adb;
import java.net.*;
import java.io.*;

/** Read-only TCP probe. An open port is never presented as successful ADB authorization. */
public final class ConnectionDiagnostic {
  public static String probe(String host,int port) {
    String prefix="目标："+host+":"+port+"\n\n";
    try {
      InetAddress[] addresses=InetAddress.getAllByName(host);
      Exception last=null;
      for(int i=0;i<Math.min(addresses.length,3);i++) {
        try(Socket socket=new Socket()) {
          socket.connect(new InetSocketAddress(addresses[i],port),2000);
          return prefix+"✓ 地址解析成功："+addresses[i].getHostAddress()+"\n✓ TCP 端口可以连通\n\n端口连通不代表已通过 ADB 授权，也不能证明该端口提供 ADB。请点击连接设备，留意电视上的授权提示。若握手失败，请核对是否误填加密配对端口或其他服务端口。";
        }catch(IOException e){last=e;}
      }
      return prefix+reason(last)+"\n\n原始信息："+last;
    }catch(UnknownHostException e){return prefix+"✕ 地址解析失败。请使用电视网络设置里显示的当前 IP，并检查手机网络。\n\n原始信息："+e;}
  }
  static String reason(Exception e) {
    if(e instanceof SocketTimeoutException)return "✕ 端口连接超时。可能是地址变化、设备离线或网络隔离，不能仅据此认定电视关闭。请核对同一 Wi-Fi、电视当前 IP 和路由器访客隔离。";
    if(e instanceof ConnectException)return "✕ 端口拒绝连接或不可达。请确认电视已开启网络 ADB、实际端口正确；也可能是网络设备拒绝了连接。";
    if(e instanceof NoRouteToHostException)return "✕ 没有可用路由。请检查手机和电视网络，以及 VPN、访客网络或隔离设置。";
    return "✕ 网络探测未完成，请检查手机网络与电视地址。";
  }
}
