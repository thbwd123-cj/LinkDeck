package cn.linkdeck.adb;
public final class PaletteChecks {
 static int checks;static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
 static double lum(int c){double result=0;double[] w={.2126,.7152,.0722};for(int i=0;i<3;i++){double v=((c>>(16-i*8))&255)/255.;result+=w[i]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return result;}
 static double contrast(int a,int b){double x=lum(a),y=lum(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 public static void main(String[] args){
 int[] colors={0xff0b131e,0xffedf5ff,0xffa4b3c8,0xff4ce1cd,0xff162430,0xff062d2a,0xff344557};
 for(String id:UiPalette.IDS){UiPalette.select(id);
  for(int c:colors){int x=UiPalette.color(c);check(Math.abs(lum(c)-lum(x))<.008,"luminance preserved: "+id);check((x>>>24)==255,"opacity preserved");if(id.equals("original"))check(x==c,"original theme unchanged");}
  for(int fg:new int[]{0xffedf5ff,0xffa4b3c8})for(int bg:new int[]{0xff0b131e,0xff162430})check(contrast(UiPalette.color(fg),UiPalette.color(bg))>=4.5,"readable text "+id);
  check(contrast(UiPalette.color(0xff062d2a),UiPalette.color(0xff4ce1cd))>=4.5,"primary button contrast "+id);
  check(UiPalette.color(0xffff9292)==0xffff9292,"warning unchanged");
  check((UiPalette.color(0x284ce1cd)>>>24)==0x28,"ripple alpha preserved");
 }
 UiPalette.select("invalid");check(UiPalette.current()==0,"invalid preference falls back");check(UiPalette.themeName().equals("AppTheme"),"default resources");
 UiPalette.select("violet");check(UiPalette.themeName().equals("PaletteTheme2")&&UiPalette.dialogName().equals("PaletteDialog2"),"matched resources");
 System.out.println("PALETTE PASSED: "+checks+" checks");
 }
}
