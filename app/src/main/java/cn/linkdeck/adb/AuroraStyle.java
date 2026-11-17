package cn.linkdeck.adb;
/** Visual tokens only; existing preference IDs and theme switching are unchanged. */
public final class AuroraStyle {
 public static boolean light(){return UiPalette.current()==4;}
 public static int base(){return new int[]{0xff071c24,0xff0b201c,0xff181427,0xff201a11,0xfff4f7fa}[UiPalette.current()];}
 public static int accent(){return UiPalette.color(0xff4ce1cd);}
 public static int secondary(){return new int[]{0xff8874ed,0xff6caec5,0xff70a6df,0xffc27a79,0xff9189c7}[UiPalette.current()];}
 public static final int GREEN=0xff2ef2c4,VIOLET=0xff7a5cff;
 public static final float GREEN_ALPHA=.5f,VIOLET_ALPHA=.42f,GLOW_ALPHA=.5f;
 public static final float GREEN_DIAMETER=340,VIOLET_DIAMETER=300,SPOT_BLUR=46,GLASS_BLUR=12;
 public static int glowStart(){return UiPalette.current()==0||light()?GREEN:accent();}
 public static int glowEnd(){return UiPalette.current()==0||light()?VIOLET:secondary();}
 public static float greenDrift(float seconds){return (float)Math.sin(seconds*2*Math.PI/20);}
 public static float violetDrift(float seconds){return (float)Math.sin(seconds*2*Math.PI/26);}
 public static int glassTint(){return light()?surface():base();}
 public static int glassTopAlpha(){return light()?205:175;}
 public static int glassBottomAlpha(){return light()?225:185;}
 public static int alpha(int c,int a){return (c&0xffffff)|(Math.max(0,Math.min(255,a))<<24);}
 public static int mix(int a,int b,float t){float u=1-t;return 0xff000000|((int)(((a>>16)&255)*u+((b>>16)&255)*t)<<16)|((int)(((a>>8)&255)*u+((b>>8)&255)*t)<<8)|(int)((a&255)*u+(b&255)*t);}
 public static int surface(){return light()?0xfff8fafc:mix(base(),0xffffffff,.06f);}
 public static int edge(){return light()?0xffacbec8:0xffb9dbdf;}
 private AuroraStyle(){}
}
