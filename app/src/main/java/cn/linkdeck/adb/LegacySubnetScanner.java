package cn.linkdeck.adb;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.net.DhcpInfo;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-initiated traditional ADB scan on the phone's current Wi-Fi IPv4 segment. */
public final class LegacySubnetScanner {
  public interface Found {void onFound(String host);}
  public interface Scope {void onScope(String scope);}
  public static final class Result {
    public final List<String> hosts;
    public final int checked,total;
    public final String scope;
    Result(List<String> hosts,int checked,int total,String scope){this.hosts=hosts;this.checked=checked;this.total=total;this.scope=scope;}
  }
  private LegacySubnetScanner() {}
  private static boolean adb(String host,Network wifi){
    try(Socket socket=wifi.getSocketFactory().createSocket()){
      // Route through physical Wi-Fi, never through the phone's VPN default.
      socket.connect(new InetSocketAddress(host,5555),350);
      return true;
    }
    // Never retry through the default route: a VPN can synthesize successful
    // TCP connects for unrelated LAN addresses and flood the candidate list.
    catch(Exception ignored){return false;}
  }
  public static Result scan(Context context,AtomicBoolean cancelled,String preferredHost,Found listener)throws Exception{
    return scan(context,cancelled,preferredHost,listener,scope->{});
  }
  public static Result scan(Context context,AtomicBoolean cancelled,String preferredHost,Found listener,Scope onScope)throws Exception{
    ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if(cm==null)throw new IllegalStateException("无法读取 Wi-Fi 网络");
    WifiManager wm=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    byte[] actualIp=null;int actualPrefix=-1;
    if(wm!=null){
      try{
        DhcpInfo info=wm.getDhcpInfo();
        if(info!=null){byte[] ip=WifiIpv4.address(info.ipAddress);
          if(WifiIpv4.usable(ip)){actualIp=ip;actualPrefix=WifiIpv4.prefix(info.netmask);}
        }
        if(actualIp==null&&wm.getConnectionInfo()!=null){
          byte[] ip=WifiIpv4.address(wm.getConnectionInfo().getIpAddress());
          if(WifiIpv4.usable(ip))actualIp=ip;
        }
      }catch(SecurityException ignored){}
    }
    Network wifi=null;List<String> hosts=null;String scope=null;
    byte[] fallbackIp=null;int fallbackPrefix=-1;Network fallbackNetwork=null;
    for(Network network:cm.getAllNetworks()){
      NetworkCapabilities capability=cm.getNetworkCapabilities(network);
      if(capability==null||!capability.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
          ||capability.hasTransport(NetworkCapabilities.TRANSPORT_VPN))continue;
      LinkProperties properties=cm.getLinkProperties(network);if(properties==null)continue;
      for(LinkAddress address:properties.getLinkAddresses()){
        if(!(address.getAddress() instanceof Inet4Address))continue;
        byte[] ip=address.getAddress().getAddress();
        if(!WifiIpv4.usable(ip))continue;
        if(actualIp!=null&&!Arrays.equals(ip,actualIp))continue;
        if(address.getPrefixLength()<16||address.getPrefixLength()>30)continue;
        fallbackIp=ip;fallbackPrefix=address.getPrefixLength();fallbackNetwork=network;break;
      }
      if(fallbackIp!=null)break;
    }
    byte[] ip=actualIp!=null?actualIp:fallbackIp;
    int prefix=fallbackIp!=null?fallbackPrefix:actualPrefix;
    if(ip==null||prefix<16||prefix>30||fallbackNetwork==null)
      throw new IllegalStateException("无法确定实体 Wi-Fi 的 IPv4 路由，请检查 Wi-Fi 网络或手动连接。");
    hosts=SubnetAddresses.around(ip,prefix);
    wifi=fallbackNetwork;
    scope=WifiIpv4.display(ip)+"/"+Math.max(24,prefix);
    onScope.onScope(scope);
    String preferred=preferredHost==null?"":preferredHost.trim();
    if(hosts.remove(preferred))hosts.add(0,preferred);
    final Network route=wifi;
    ExecutorService pool=Executors.newFixedThreadPool(24);
    ExecutorCompletionService<String> completions=new ExecutorCompletionService<>(pool);
    List<Future<String>> tasks=new ArrayList<>();
    List<String> found=new ArrayList<>();int checked=0;
    try{
      for(String host:hosts){
        if(cancelled.get())break;
        tasks.add(completions.submit((Callable<String>)()->cancelled.get()?null:adb(host,route)?host:null));
      }
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
      for(int i=0;i<tasks.size()&&!cancelled.get();i++){
        long remain=deadline-System.nanoTime();if(remain<=0)break;
        Future<String> completed=completions.poll(remain,TimeUnit.NANOSECONDS);
        if(completed==null)break;checked++;
        String candidate=completed.get();if(candidate!=null){found.add(candidate);listener.onFound(candidate);}
      }
      return new Result(found,checked,tasks.size(),scope);
    }finally{
      cancelled.set(true);
      for(Future<String> task:tasks)task.cancel(true);
      pool.shutdownNow();
    }
  }
}
