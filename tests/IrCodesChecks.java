package cn.linkdeck.adb;
import java.nio.file.*;

public final class IrCodesChecks {
  private static int checks;
  private static void check(boolean value){checks++;if(!value)throw new AssertionError("IR check "+checks);}
  public static void main(String[] args) throws Exception{
    int[] p=IrCodes.nec(0,0x9f,0x57,0xa8);
    check(p.length==67&&p[0]==9000&&p[1]==4500&&p[66]==560);
    check(p[2]==560&&p[3]==560); // first address bit is zero
    check(p[18]==560&&p[19]==1690); // first bit of 0x9f is one
    check(IrCodes.skyworthQPlus().key("VOL_UP")!=null);
    String raw="Filetype: IR signals file\nVersion: 1\nname: Vol_up\ntype: raw\nfrequency: 38000\ndata: 1000 2000 3000\n";
    IrCodes parsed=IrCodes.fromFlipper(raw);
    check(parsed.key("VOL_UP")!=null&&parsed.key("VOL_UP").pattern.length==3);
    String nec="name: Ok\ntype: parsed\nprotocol: NECext\naddress: 00 9F 00 00\ncommand: 02 FD 00 00\n";
    check(IrCodes.fromFlipper(nec).key("OK").pattern[0]==9000);
    String standard="name: Power\ntype: parsed\nprotocol: NEC\naddress: 04 00 00 00\ncommand: 08 00 00 00\n";
    int[] encoded=IrCodes.fromFlipper(standard).key("POWER").pattern;
    check(encoded[18]==560&&encoded[19]==1690); // address complement starts with a one
    check(encoded[50]==560&&encoded[51]==1690); // command complement starts with a one
    String samsung="name: power\ntype: parsed\nprotocol: Samsung32\naddress: 0E 00 00 00\ncommand: 0C 00 00 00\n";
    int[] sp=IrCodes.fromFlipper(samsung).key("POWER").pattern;
    check(sp.length==67&&sp[0]==4500&&sp[1]==4500&&sp[66]==550);
    check(sp[2]==550&&sp[3]==550&&sp[20]==550&&sp[21]==1650); // repeated address, LSB first
    Path proven=Paths.get("app/src/main/assets/ir/Skyworth_8H66_H7_SystemIR.ir");
    IrCodes tv=IrCodes.fromFlipper(new String(Files.readAllBytes(proven),"UTF-8"));
    for(String name:IrVerification.REQUIRED)check(tv.has(name));
    check(tv.has("SOURCE"));
    check(java.util.Arrays.equals(tv.key("POWER").pattern,IrCodes.samsung32(0x0e,0x0c)));
    check(java.util.Arrays.equals(tv.key("UP").pattern,IrCodes.samsung32(0x0e,0x42)));
    check(java.util.Arrays.equals(tv.key("RIGHT").pattern,IrCodes.samsung32(0x0e,0x45)));
    check(java.util.Arrays.equals(tv.key("SOURCE").pattern,IrCodes.samsung32(0x0e,0x0f)));
    java.util.Set<String> partial=IrVerification.parse("VOL_UP,RIGHT,POWER");
    check(!IrVerification.complete(tv,partial));
    check(IrVerification.missing(tv,partial).size()==9);
    java.util.Set<String> full=new java.util.LinkedHashSet<>(java.util.Arrays.asList(IrVerification.REQUIRED));
    check(IrVerification.complete(tv,full));
    check(IrVerification.parse(IrVerification.serialize(full)).equals(full));
    IrCodes.Signal alternative=IrCodes.skyworthQPlus().key("UP");
    IrCodes repaired=tv.replacing("UP",alternative);
    check(!IrCodes.sameSignal(tv.key("UP"),repaired.key("UP")));
    check(IrCodes.sameSignal(tv.key("POWER"),repaired.key("POWER")));
    IrCodes restored=IrCodes.fromFlipper(repaired.toFlipperRaw());
    check(IrCodes.sameSignal(restored.key("UP"),alternative));
    check(IrCodes.sameSignal(restored.key("POWER"),tv.key("POWER")));
    check(!IrCodes.sameSignal(alternative,tv.key("UP")));
    check(tv.toFlipperRaw().equals(IrCodes.fromFlipper(tv.toFlipperRaw()).toFlipperRaw()));
    try{IrCodes.fromFlipper("name: Power\ntype: parsed\nprotocol: RC5\naddress: 00 00\ncommand: 01 FE\n");throw new AssertionError("RC5 accepted");}catch(IllegalArgumentException expected){checks++;}
    Path catalog=Paths.get("app/src/main/assets/ir");int count=0;
    for(String line:Files.readAllLines(catalog.resolve("catalog.tsv"))){
      String[] row=line.split("\t");check(row.length==3);
      IrCodes candidate=IrCodes.fromFlipper(new String(Files.readAllBytes(catalog.resolve(row[2]+".ir")),"UTF-8"));
      check(candidate.has("POWER")&&candidate.has("VOL_UP"));count++;
      check(!java.util.Arrays.equals(candidate.key("POWER").pattern,candidate.key("VOL_UP").pattern));
      if(candidate.has("RIGHT"))check(!java.util.Arrays.equals(candidate.key("RIGHT").pattern,candidate.key("VOL_UP").pattern));
    }
    check(count>=71);
    System.out.println("IR CODES PASSED: "+checks+" checks");
  }
}
