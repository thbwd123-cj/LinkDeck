package cn.linkdeck.adb;
/** Visual tokens only; existing preference IDs and theme switching are unchanged. */
public final class AuroraStyle {
 public static boolean light(){return UiPalette.current()==4;}
 public static int base(){return new int[]{0xff071c24,0xff0b201c,0xff181427,0xff201a11,0xfff4f7fa}[UiPalette.current()];}
 public static int baseTop(){return light()?0xfff4f7fa:new int[]{0xff0a2e3a,0xff0f2e26,0xff241d33,0xff2a2113,0xfff4f7fa}[UiPalette.current()];}
 public static int baseBottom(){return base();}
 public static int accent(){return UiPalette.color(0xff4ce1cd);}
 public static int secondary(){return new int[]{0xff8874ed,0xff6caec5,0xff70a6df,0xffc27a79,0xff9189c7}[UiPalette.current()];}
 public static final int GREEN=0xff2ef2c4,VIOLET=0xff7a5cff,PRIMARY_TEXT=0xff03211b;
 public static final int SOFT_START=0xff67e8f9,SOFT_MID=0xff38bdf8,SOFT_END=0xff818cf8,SOFT_TEXT=0xff0b2a3d;
 public static int[] primaryStops(){return light()?new int[]{SOFT_START,SOFT_MID,SOFT_END}:new int[]{glowStart(),accent(),glowEnd()};}
 public static int primaryText(){return light()?SOFT_TEXT:PRIMARY_TEXT;}
 public static int frameStart(){return light()?SOFT_START:glowStart();}
 public static int frameEnd(){return light()?SOFT_END:glowEnd();}
 public static final float GREEN_ALPHA=.6f,VIOLET_ALPHA=.52f,GLOW_ALPHA=.5f;
 public static final float GREEN_DIAMETER=340,VIOLET_DIAMETER=300,SPOT_BLUR=46,GLASS_BLUR=12,SPOT_FADE=.65f;
 public static final int GLASS_SHEEN_TOP=0x1cffffff,GLASS_SHEEN_MID=0x0bffffff,GLASS_SHEEN_BOTTOM=0x12ffffff;
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
