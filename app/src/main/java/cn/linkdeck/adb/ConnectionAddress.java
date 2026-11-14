package cn.linkdeck.adb;

/** Parses a manually entered address without contacting the target. */
public final class ConnectionAddress {
  public final String host;
  public final int port;
  private ConnectionAddress(String host,int port){this.host=host;this.port=port;}
  public static ConnectionAddress parse(String address,String portText){
    String host=address==null?"":address.trim(),p=portText==null?"":portText.trim();
    if(host.startsWith("[")){
      int end=host.indexOf(']');
      if(end<2)throw new IllegalArgumentException("IPv6 地址格式错误");
      String rest=host.substring(end+1);
      if(!rest.isEmpty()&&!rest.startsWith(":"))throw new IllegalArgumentException("地址格式错误");
      if(rest.length()>1)p=rest.substring(1);
      host=host.substring(1,end);
    }else{
      int first=host.indexOf(':'),last=host.lastIndexOf(':');
      if(first>0&&first==last){p=host.substring(first+1);host=host.substring(0,first);}
      else if(first>=0)throw new IllegalArgumentException("IPv6 地址请用 [地址]:端口 格式");
    }
    if(host.isEmpty()||host.contains("/")||host.contains(" ")||host.contains("[")||host.contains("]")||host.contains("@")||host.contains("\\")||host.contains("\n")||host.contains("\r"))throw new IllegalArgumentException("地址格式错误");
    if(!p.matches("[0-9]{1,5}"))throw new IllegalArgumentException("端口格式错误");
    int port=Integer.parseInt(p);
    if(port<1||port>65535)throw new IllegalArgumentException("端口超出范围");
    return new ConnectionAddress(host,port);
  }
}
