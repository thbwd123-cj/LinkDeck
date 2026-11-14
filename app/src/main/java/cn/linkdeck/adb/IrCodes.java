package cn.linkdeck.adb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Limited, offline Flipper IR profile reader and NEC pulse encoder. */
public final class IrCodes {
  public static final class Signal {
    public final int frequency;
    public final int[] pattern;
    Signal(int frequency,int[] pattern){this.frequency=frequency;this.pattern=pattern;}
  }
  private final Map<String,Signal> keys;
  private IrCodes(Map<String,Signal> keys){this.keys=Collections.unmodifiableMap(keys);}
  public Signal key(String name){return keys.get(name);}
  public int size(){return keys.size();}
  public boolean has(String name){return keys.containsKey(name);}
  public IrCodes replacing(String name,Signal signal){
    if(!name.matches("POWER|UP|DOWN|LEFT|RIGHT|OK|BACK|HOME|MENU|MUTE|VOL_UP|VOL_DOWN|SOURCE")||signal==null)
      throw new IllegalArgumentException("无效的红外按键");
    Map<String,Signal> copy=new HashMap<>(keys);copy.put(name,signal);
    return new IrCodes(copy);
  }
  public static boolean sameSignal(Signal a,Signal b){return a!=null&&b!=null&&a.frequency==b.frequency&&Arrays.equals(a.pattern,b.pattern);}
  /** Export a self-contained Flipper profile so repaired keys survive app reinstalls. */
  public String toFlipperRaw(){
    StringBuilder out=new StringBuilder("Filetype: IR signals file\nVersion: 1\n");
    List<String> names=new ArrayList<>(keys.keySet());Collections.sort(names);
    for(String name:names){Signal signal=keys.get(name);
      out.append("#\nname: ").append(name).append("\ntype: raw\nfrequency: ").append(signal.frequency).append("\ndata:");
      for(int duration:signal.pattern)out.append(' ').append(duration);
      out.append('\n');
    }
    if(out.length()>65536)throw new IllegalArgumentException("组合码超过 64 KB，请选择较短的遥控码");
    return out.toString();
  }
  public static int[] nec(int addressLow,int addressHigh,int command,int commandInverse){
    int[] data={addressLow,addressHigh,command,commandInverse};
    int[] p=new int[2+32*2+1];p[0]=9000;p[1]=4500;
    int at=2;
    for(int b:data)for(int bit=0;bit<8;bit++){
      p[at++]=560;p[at++]=((b>>>bit)&1)!=0?1690:560;
    }
    p[at]=560;return p;
  }
  /** Flipper Samsung32 frame: address twice, command, inverse command; LSB first. */
  public static int[] samsung32(int address,int command){
    int[] data={address,address,command,command^255};
    int[] p=new int[67];p[0]=4500;p[1]=4500;
    int at=2;
    for(int b:data)for(int bit=0;bit<8;bit++){
      p[at++]=550;p[at++]=((b>>>bit)&1)!=0?1650:550;
    }
    p[at]=550;return p;
  }
  /** Public Skyworth Q+ box example; not asserted compatible with any TV. */
  public static IrCodes skyworthQPlus(){
    String[] names={"POWER","UP","LEFT","RIGHT","DOWN","HOME","MENU","OK","BACK","VOL_DOWN","VOL_UP","MUTE"};
    int[] commands={0x57,0x43,0x06,0x0e,0x0a,0x47,0x16,0x02,0x4f,0x5d,0xff,0x8a};
    Map<String,Signal> result=new HashMap<>();
    for(int i=0;i<names.length;i++)result.put(names[i],new Signal(38000,nec(0x00,0x9f,commands[i],commands[i]^255)));
    return new IrCodes(result);
  }
  private static String canonical(String name){
    String n=name.trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_');
    if(n.equals("VOLUMEUP")||n.equals("VOLUME_UP")||n.equals("VOLUP")||n.equals("VOL+"))return "VOL_UP";
    if(n.equals("VOLUMEDOWN")||n.equals("VOLUME_DOWN")||n.equals("VOLDOWN")||n.equals("VOL_DN")||n.equals("VOL-")||n.equals("VOL_DOWN"))return "VOL_DOWN";
    if(n.equals("ENTER")||n.equals("SELECT"))return "OK";
    if(n.equals("EXIT")||n.equals("ESC"))return "BACK";
    if(n.equals("POWER_TOGGLE"))return "POWER";
    if(n.equals("INPUT")||n.equals("INPUT_SOURCE")||n.equals("TV_INPUT"))return "SOURCE";
    if(n.equals("CENTER"))return "OK";
    return n;
  }
  private static int hexByte(String text,int index){
    String[] parts=text.trim().split("\\s+");
    if(parts.length<index+1)throw new IllegalArgumentException("红外编码缺少字节");
    int n=Integer.parseInt(parts[index],16);
    if(n<0||n>255)throw new IllegalArgumentException("红外编码字节超出范围");
    return n;
  }
  private static Signal parse(Map<String,String> values){
    String type=values.get("type");
    if("raw".equalsIgnoreCase(type)){
      int frequency=Integer.parseInt(values.getOrDefault("frequency","38000"));
      String[] data=values.getOrDefault("data","").trim().split("\\s+");
      if(data.length<3||data.length>1200||frequency<20000||frequency>60000)throw new IllegalArgumentException("原始红外数据无效");
      int[] p=new int[data.length];long total=0;
      for(int i=0;i<p.length;i++){p[i]=Integer.parseInt(data[i]);if(p[i]<1||p[i]>150000)throw new IllegalArgumentException("脉冲时长无效");total+=p[i];}
      if(total>1900000)throw new IllegalArgumentException("单键红外数据过长");
      return new Signal(frequency,p);
    }
    if(!"parsed".equalsIgnoreCase(type))throw new IllegalArgumentException("不支持的红外数据类型");
    String protocol=values.getOrDefault("protocol","");
    if(!protocol.equalsIgnoreCase("NEC")&&!protocol.equalsIgnoreCase("NECext")&&!protocol.equalsIgnoreCase("Samsung32"))throw new IllegalArgumentException("仅支持 NEC、NECext、Samsung32 和 raw");
    int a=hexByte(values.getOrDefault("address",""),0),b=hexByte(values.getOrDefault("address",""),1);
    int c=hexByte(values.getOrDefault("command",""),0),d=hexByte(values.getOrDefault("command",""),1);
    if(protocol.equalsIgnoreCase("Samsung32"))return new Signal(38000,samsung32(a,c));
    if(protocol.equalsIgnoreCase("NEC"))return new Signal(38000,nec(a,a^255,c,c^255));
    return new Signal(38000,nec(a,b,c,c^255));
  }
  public static IrCodes fromFlipper(String input){
    if(input==null||input.length()>65536)throw new IllegalArgumentException("红外文件超过 64 KB");
    Map<String,Signal> output=new HashMap<>();Map<String,String> block=new HashMap<>();
    String normalized=input.replace("\r","")+"\nname: END\n";
    for(String line:normalized.split("\n")){
      if(line.startsWith("#")||line.trim().isEmpty())continue;
      int colon=line.indexOf(':');if(colon<0)continue;
      String key=line.substring(0,colon).trim().toLowerCase(Locale.ROOT),value=line.substring(colon+1).trim();
      if(key.equals("name")){
        if(block.containsKey("name")){
          String name=canonical(block.get("name"));
          if(name.matches("POWER|UP|DOWN|LEFT|RIGHT|OK|BACK|HOME|MENU|MUTE|VOL_UP|VOL_DOWN|SOURCE")){
            output.put(name,parse(block));if(output.size()>20)throw new IllegalArgumentException("按键数过多");
          }
        }
        block.clear();block.put("name",value);
      }else if(block.containsKey("name"))block.put(key,value);
    }
    if(output.isEmpty())throw new IllegalArgumentException("文件没有可用按键；请导入 Flipper .ir 格式");
    return new IrCodes(output);
  }
}
