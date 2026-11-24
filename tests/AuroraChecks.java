package cn.linkdeck.adb;
import java.util.*;
public final class AuroraChecks {
 static int checks;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
 static double luminance(int c){double total=0;double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double x=((c>>(16-i*8))&255)/255.;total+=weights[i]*(x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4));}return total;}
 static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 static int screen(int front,int back,int alpha){int out=0xff000000;for(int shift:new int[]{16,8,0}){double f=((front>>shift)&255)/255.,b=((back>>shift)&255)/255.;int value=(int)Math.round(255*(b+(1-b)*f*alpha/255.));out|=value<<shift;}return out;}
 static int over(int front,int back,int alpha){return AuroraStyle.mix(back,front,alpha/255f);}
 public static void main(String[] args){
  for(String id:UiPalette.IDS){UiPalette.select(id);int base=AuroraStyle.base(),surface=AuroraStyle.surface();
   int maxGlow=screen(AuroraStyle.glowEnd(),screen(AuroraStyle.glowStart(),base,128),107);
   int glass=over(AuroraStyle.glassTint(),maxGlow,AuroraStyle.glassTopAlpha());
   int key=AuroraStyle.mix(surface,AuroraStyle.light()?0xffffffff:AuroraStyle.edge(),AuroraStyle.light()?.6f:.075f);
   for(int bg:new int[]{base,surface,glass,key})for(int fg:new int[]{UiPalette.color(0xffedf5ff),UiPalette.color(0xffa4b3c8)})check(contrast(fg,bg)>=4.5,"Text contrast "+id+" "+Integer.toHexString(bg)+" = "+contrast(fg,bg));
   if(AuroraStyle.light())check(AuroraStyle.baseTop()==AuroraStyle.baseBottom(),"Light theme keeps a flat base");
   else check(luminance(AuroraStyle.baseTop())>luminance(AuroraStyle.baseBottom()),"Dark sky lifts toward the top "+id);
   if(AuroraStyle.light())check(java.util.Arrays.equals(AuroraStyle.primaryStops(),new int[]{0xff67e8f9,0xff38bdf8,0xff818cf8}),"Light primary gradient runs soft cyan, sky, indigo "+id);
   else check(java.util.Arrays.equals(AuroraStyle.primaryStops(),new int[]{AuroraStyle.glowStart(),AuroraStyle.accent(),AuroraStyle.glowEnd()}),"Primary gradient runs glowStart, accent, glowEnd "+id);
   check(AuroraStyle.primaryText()==(AuroraStyle.light()?0xff0b2a3d:AuroraStyle.PRIMARY_TEXT),"Primary text ink follows theme "+id);
   check(contrast(AuroraStyle.primaryText(),AuroraStyle.primaryStops()[0])>=4.5,"Primary text readable on gradient start "+id);
   check(contrast(AuroraStyle.primaryText(),AuroraStyle.primaryStops()[2])>=3,"Primary text readable on gradient end "+id);
   if(AuroraStyle.light())check(AuroraStyle.frameStart()==0xff67e8f9&&AuroraStyle.frameEnd()==0xff818cf8,"Light card frame runs soft cyan to indigo "+id);
   check(contrast(AuroraStyle.accent(),surface)>=3,"Selected navigation visible "+id);
  }
  check(AuroraStyle.GREEN==0xff2ef2c4&&AuroraStyle.VIOLET==0xff7a5cff,"Prototype colors");
  check(AuroraStyle.PRIMARY_TEXT==0xff03211b,"Prototype primary text ink");
  check(AuroraStyle.GREEN_ALPHA==.6f&&AuroraStyle.VIOLET_ALPHA==.52f,"Visible spot opacity");
  check(AuroraStyle.GREEN_DIAMETER==340&&AuroraStyle.VIOLET_DIAMETER==300&&AuroraStyle.SPOT_BLUR==46,"Spot geometry");
  check(AuroraStyle.GLASS_BLUR>=10&&AuroraStyle.GLASS_BLUR<=12,"Frost radius");
  check(Math.abs(AuroraStyle.greenDrift(5)-1)<.001&&Math.abs(AuroraStyle.greenDrift(15)+1)<.001&&Math.abs(AuroraStyle.greenDrift(20))<.001,"20 second return period");
  check(Math.abs(AuroraStyle.violetDrift(6.5f)-1)<.001&&Math.abs(AuroraStyle.violetDrift(19.5f)+1)<.001&&Math.abs(AuroraStyle.violetDrift(26))<.001,"26 second return period");
  check(AuroraStyle.SPOT_FADE==.65f&&AuroraStyle.GLASS_SHEEN_TOP==0x1cffffff&&AuroraStyle.GLASS_SHEEN_MID==0x0bffffff&&AuroraStyle.GLASS_SHEEN_BOTTOM==0x12ffffff,"Prototype blob falloff and glass sheen");
  UiPalette.select("original");int plain=AuroraStyle.base(),green=screen(AuroraStyle.glowStart(),plain,Math.round(255*AuroraStyle.GREEN_ALPHA)),violet=screen(AuroraStyle.glowEnd(),plain,Math.round(255*AuroraStyle.VIOLET_ALPHA));
  check(((green>>8)&255)-((plain>>8)&255)>100,"Green peak remains visible in a static frame");
  check((violet&255)-(plain&255)>80,"Violet peak remains visible in a static frame");
  Random random=new Random(198);for(int w:new int[]{1,3,7,96})for(int h:new int[]{1,5,11})for(int radius:new int[]{0,1,3}){
   int[] a=new int[w*h],tmp=new int[w*h];Arrays.fill(a,0xff183b52);AuroraBlur.apply(a,tmp,w,h,radius);for(int v:a)check(v==0xff183b52,"Uniform field remains uniform");
   for(int i=0;i<a.length;i++)a[i]=0xff000000|random.nextInt(0xffffff);int[] before=a.clone();AuroraBlur.apply(a,tmp,w,h,radius);
   for(int v:a)check((v>>>24)==255,"Blur stays opaque");if(radius==0)check(Arrays.equals(before,a),"Radius zero is identity");
  }
  int[] impulse=new int[81],tmp=new int[81];Arrays.fill(impulse,0xff000000);impulse[40]=0xffffffff;AuroraBlur.apply(impulse,tmp,9,9,2);
  check((impulse[40]&255)>0&&(impulse[40]&255)<255,"Blur reduces peak");check(impulse[39]==impulse[41]&&impulse[31]==impulse[49],"Blur is symmetric");check((impulse[39]&255)>0,"Blur spreads light");
  System.out.println("AURORA PASSED: "+checks+" blur and five-theme contrast checks");
 }
}
