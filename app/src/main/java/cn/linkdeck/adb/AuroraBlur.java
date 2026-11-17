package cn.linkdeck.adb;
/** Separable box blur of decorative background only, never of text or screen contents. */
public final class AuroraBlur {
 public static void apply(int[] pixels,int[] work,int w,int h,int radius){
  if(w<1||h<1||radius<0||pixels.length<w*h||work.length<w*h)throw new IllegalArgumentException("blur dimensions");
  pass(pixels,work,w,h,radius,true);pass(work,pixels,w,h,radius,false);
 }
 private static void pass(int[] src,int[] dst,int w,int h,int radius,boolean horizontal){
  int length=horizontal?w:h,lines=horizontal?h:w,n=radius*2+1;
  for(int line=0;line<lines;line++){
   int red=0,green=0,blue=0;
   for(int j=-radius;j<=radius;j++){int k=Math.max(0,Math.min(length-1,j));int c=src[horizontal?line*w+k:k*w+line];red+=(c>>16)&255;green+=(c>>8)&255;blue+=c&255;}
   for(int i=0;i<length;i++){
    dst[horizontal?line*w+i:i*w+line]=0xff000000|((red/n)<<16)|((green/n)<<8)|(blue/n);
    int left=Math.max(0,i-radius),right=Math.min(length-1,i+radius+1);
    int a=src[horizontal?line*w+left:left*w+line],b=src[horizontal?line*w+right:right*w+line];
    red+=((b>>16)&255)-((a>>16)&255);green+=((b>>8)&255)-((a>>8)&255);blue+=(b&255)-(a&255);
   }
  }
 }
 private AuroraBlur(){}
}
