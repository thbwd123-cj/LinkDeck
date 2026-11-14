package cn.linkdeck.adb;

import java.util.ArrayList;
import java.util.List;

/** Bounds a user-requested legacy scan to the phone's current IPv4 Wi-Fi segment. */
public final class SubnetAddresses {
  private SubnetAddresses() {}
  public static List<String> around(byte[] address,int prefix){
    if(address==null||address.length!=4||prefix<16||prefix>30)throw new IllegalArgumentException("Wi-Fi IPv4 subnet unavailable");
    int ip=0;for(byte b:address)ip=(ip<<8)|(b&255);
    int bits=Math.max(prefix,24),mask=-1<<(32-bits),network=ip&mask;
    List<String> hosts=new ArrayList<>();
    for(int i=1;i<((1<<(32-bits))-1);i++){
      int candidate=network+i;if(candidate==ip)continue;
      hosts.add(((candidate>>>24)&255)+"."+((candidate>>>16)&255)+"."+((candidate>>>8)&255)+"."+(candidate&255));
    }
    return hosts;
  }
}
