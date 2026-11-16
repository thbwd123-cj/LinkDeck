package cn.linkdeck.adb;
/** Shared dark palettes. Color tables preserve luminance and warning hues. */
public final class UiPalette {
 public static final String[] IDS={"original","forest","violet","amber"};
 public static final String[] NAMES={"青蓝 · 默认","松绿","紫罗兰","琥珀"};
 private static volatile int selected;
 public static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return 0;}
 public static void select(String id){selected=index(id);}
 public static int current(){return selected;}
 public static String themeName(){return selected==0?"AppTheme":"PaletteTheme"+selected;}
 public static String dialogName(){return selected==0?"SoftDialog":"PaletteDialog"+selected;}
 public static int color(int original){if(selected==0)return original;switch(original){
case 0x00225168: return selected==1?0x001c5634:selected==2?0x00633c79:0x00634720;
case 0x002e8a80: return selected==1?0x00338d58:selected==2?0x00956dad:0x00987748;
case 0x0048cdb9: return selected==1?0x0052d087:selected==2?0x00d2abe8:0x00dbb37a;
case 0x004ce1cd: return selected==1?0x005ce495:selected==2?0x00e1bff4:0x00ecc58e;
case 0x122e8a80: return selected==1?0x12338d58:selected==2?0x12956dad:0x12987748;
case 0x150e887c: return selected==1?0x15158b46:selected==2?0x159765b3:0x159c7136;
case 0x1648cdb9: return selected==1?0x1652d087:selected==2?0x16d2abe8:0x16dbb37a;
case 0x2256e5d1: return selected==1?0x2266e79c:selected==2?0x22e4c4f6:0x22efca96;
case 0x250e887c: return selected==1?0x25158b46:selected==2?0x259765b3:0x259c7136;
case 0x284ce1cd: return selected==1?0x285ce495:selected==2?0x28e1bff4:0x28ecc58e;
case 0x30225168: return selected==1?0x301c5634:selected==2?0x30633c79:0x30634720;
case 0x334ce1cd: return selected==1?0x335ce495:selected==2?0x33e1bff4:0x33ecc58e;
case 0x4453edcf: return selected==1?0x4465ef9e:selected==2?0x44e9cbfa:0x44f5d09d;
case 0x5554dfca: return selected==1?0x5561e297:selected==2?0x55dfbef3:0x55eac48f;
case 0x664ce1cd: return selected==1?0x665ce495:selected==2?0x66e1bff4:0x66ecc58e;
case 0xff03070c: return selected==1?0xff020805:selected==2?0xff0a050e:0xff090602;
case 0xff030e16: return selected==1?0xff021008:selected==2?0xff14081a:0xff120c02;
case 0xff040b13: return selected==1?0xff030d07:selected==2?0xff100716:0xff0e0a03;
case 0xff040e18: return selected==1?0xff031008:selected==2?0xff14081c:0xff130c03;
case 0xff060e16: return selected==1?0xff041009:selected==2?0xff130919:0xff120c05;
case 0xff062029: return selected==1?0xff052211:selected==2?0xff291435:0xff291a06;
case 0xff062d2a: return selected==1?0xff072e18:selected==2?0xff351e42:0xff34250f;
case 0xff07534e: return selected==1?0xff0a552a:selected==2?0xff603a76:0xff61441b;
case 0xff090f17: return selected==1?0xff07110b:selected==2?0xff140b19:0xff130e07;
case 0xff091c24: return selected==1?0xff071e11:selected==2?0xff23132d:0xff231809;
case 0xff0a131f: return selected==1?0xff07160d:selected==2?0xff1a0d22:0xff181108;
case 0xff0b111a: return selected==1?0xff08130d:selected==2?0xff160d1c:0xff151009;
case 0xff0b131e: return selected==1?0xff08150d:selected==2?0xff1a0e21:0xff181209;
case 0xff0d1724: return selected==1?0xff091a10:selected==2?0xff1f1127:0xff1d150a;
case 0xff0e1c28: return selected==1?0xff0b1f13:selected==2?0xff24142d:0xff22190c;
case 0xff101c26: return selected==1?0xff0d1e14:selected==2?0xff23152b:0xff22190e;
case 0xff101d2c: return selected==1?0xff0c2014:selected==2?0xff261530:0xff241b0d;
case 0xff101f2b: return selected==1?0xff0d2215:selected==2?0xff271731:0xff261c0e;
case 0xff102332: return selected==1?0xff0c2617:selected==2?0xff2d1939:0xff2b1f0e;
case 0xff102731: return selected==1?0xff0e2a19:selected==2?0xff301c3b:0xff2f220f;
case 0xff102d31: return selected==1?0xff0f2f1c:selected==2?0xff352140:0xff342714;
case 0xff113f48: return selected==1?0xff104225:selected==2?0xff4b2d5d:0xff4c3516;
case 0xff13243d: return selected==1?0xff0d2919:selected==2?0xff321841:0xff2f210f;
case 0xff153d40: return selected==1?0xff153f26:selected==2?0xff462e54:0xff46341c;
case 0xff15a89e: return selected==1?0xff20ac5b:selected==2?0xffb483d1:0xffbd8e4d;
case 0xff162430: return selected==1?0xff12271b:selected==2?0xff2c1c36:0xff2b2114;
case 0xff164b54: return selected==1?0xff154e2d:selected==2?0xff58366c:0xff59401d;
case 0xff173d43: return selected==1?0xff163f27:selected==2?0xff472e55:0xff47351c;
case 0xff195252: return selected==1?0xff1c5433:selected==2?0xff5c3e6e:0xff5c4627;
case 0xff1a2d40: return selected==1?0xff143120:selected==2?0xff392247:0xff372916;
case 0xff1b2532: return selected==1?0xff16281d:selected==2?0xff2d1f35:0xff2b2317;
case 0xff1b2b3e: return selected==1?0xff152f20:selected==2?0xff372143:0xff342817;
case 0xff1c2c3a: return selected==1?0xff172f21:selected==2?0xff362340:0xff342919;
case 0xff203348: return selected==1?0xff193826:selected==2?0xff40284e:0xff3d2f1b;
case 0xff21334e: return selected==1?0xff183926:selected==2?0xff432753:0xff3f301b;
case 0xff21374b: return selected==1?0xff1a3c28:selected==2?0xff442b53:0xff42321d;
case 0xff23485c: return selected==1?0xff1d4d31:selected==2?0xff57366a:0xff564021;
case 0xff253347: return selected==1?0xff1d3828:selected==2?0xff3f2a4b:0xff3c301f;
case 0xff253e50: return selected==1?0xff1f422e:selected==2?0xff4b315a:0xff493922;
case 0xff263b4a: return selected==1?0xff203f2d:selected==2?0xff463052:0xff443623;
case 0xff269e9d: return selected==1?0xff2fa25f:selected==2?0xffab7dc5:0xffb0884f;
case 0xff286663: return selected==1?0xff2b6844:selected==2?0xff705083:0xff715937;
case 0xff2a3748: return selected==1?0xff233b2d:selected==2?0xff412f4c:0xff3f3425;
case 0xff2a6968: return selected==1?0xff2d6b47:selected==2?0xff735386:0xff745c3a;
case 0xff2b3543: return selected==1?0xff24392d:selected==2?0xff3e2f46:0xff3c3326;
case 0xff304152: return selected==1?0xff294535:selected==2?0xff4c3758:0xff4a3d2b;
case 0xff304257: return selected==1?0xff274735:selected==2?0xff4f375d:0xff4d3e2a;
case 0xff309d95: return selected==1?0xff37a063:selected==2?0xffa87dc1:0xffad8752;
case 0xff327f84: return selected==1?0xff328354:selected==2?0xff8c65a3:0xff8e6f43;
case 0xff344557: return selected==1?0xff2c4a38:selected==2?0xff513b5d:0xff4f412f;
case 0xff35485e: return selected==1?0xff2c4d3a:selected==2?0xff563d64:0xff53442f;
case 0xff364553: return selected==1?0xff2f493a:selected==2?0xff4e3d59:0xff4d4232;
case 0xff40ddc6: return selected==1?0xff50e08c:selected==2?0xffdebaf3:0xffe9c086;
case 0xff42dcc8: return selected==1?0xff52df8d:selected==2?0xffddb9f2:0xffe9c086;
case 0xff4b6175: return selected==1?0xff426651:selected==2?0xff6e557d:0xff6c5c45;
case 0xff4ce1cd: return selected==1?0xff5ce495:selected==2?0xffe1bff4:0xffecc58e;
case 0xff58e7d3: return selected==1?0xff68e99e:selected==2?0xffe5c6f7:0xfff0cc99;
case 0xff5a9b97: return selected==1?0xff5d9d78:selected==2?0xffa385b5:0xffa58c6a;
case 0xff617187: return selected==1?0xff567764:selected==2?0xff7e678c:0xff7c6e59;
case 0xff65dbce: return selected==1?0xff70de9e:selected==2?0xffddbdf0:0xffe6c393;
case 0xff68778a: return selected==1?0xff5e7d6b:selected==2?0xff836e8f:0xff817461;
case 0xff70829a: return selected==1?0xff638973:selected==2?0xff91779f:0xff8e7e68;
case 0xff728599: return selected==1?0xff678b76:selected==2?0xff927b9f:0xff90816b;
case 0xff8bffe9: return selected==1?0xff9effc6:selected==2?0xfff6e5ff:0xffffe8c7;
case 0xff9fb2ce: return selected==1?0xff90baa1:selected==2?0xffc2a7d2:0xffc0ae94;
case 0xff9fe6ca: return selected==1?0xffa1e7be:selected==2?0xffe6d0f3:0xffebd4b4;
case 0xffa1fff1: return selected==1?0xffb1ffd1:selected==2?0xfff7e9ff:0xffffebd0;
case 0xffa4b3c8: return selected==1?0xff98b9a6:selected==2?0xffbfaacc:0xffbeb09c;
case 0xffaabbd0: return selected==1?0xff9ec1ad:selected==2?0xffc7b2d4:0xffc6b7a2;
case 0xffb0bfd5: return selected==1?0xffa3c6b2:selected==2?0xffccb6d8:0xffcabca7;
case 0xffb2fff1: return selected==1?0xffbdffd9:selected==2?0xfff8ecff:0xffffeed6;
case 0xffc1fff2: return selected==1?0xffc9ffdf:selected==2?0xfff9efff:0xfffff1dd;
case 0xffe0ebfa: return selected==1?0xffd7f0e1:selected==2?0xfff3e5fb:0xfff3e9da;
case 0xffedf3ff: return selected==1?0xffe6f7ed:selected==2?0xfff9efff:0xfff9f2e8;
case 0xffedf5ff: return selected==1?0xffe7f8ee:selected==2?0xfffaf1ff:0xfffbf3e9;
case 0xffeff5ff: return selected==1?0xffe9f8ef:selected==2?0xfffaf2ff:0xfffaf4eb;
default:return original;}}
 private UiPalette(){}
}
