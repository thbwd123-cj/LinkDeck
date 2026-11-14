package cn.linkdeck.adb;
import java.io.*;import java.math.BigInteger;import java.security.*;import java.security.cert.*;import java.text.SimpleDateFormat;import java.util.*;
/** Minimal self-signed X.509 certificate for the existing app-private ADB RSA key. */
public final class TlsIdentity {
 private static byte[] der(int tag,byte[] value)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(tag);int n=value.length;if(n<128)out.write(n);else{int bytes=(32-Integer.numberOfLeadingZeros(n)+7)/8;out.write(128|bytes);for(int i=bytes-1;i>=0;i--)out.write(n>>>(i*8));}out.write(value);return out.toByteArray();}
 private static byte[] join(byte[]... values)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] v:values)out.write(v);return out.toByteArray();}
 private static byte[] seq(byte[]... v)throws IOException{return der(0x30,join(v));}
 private static byte[] integer(byte[] v)throws IOException{return der(2,v);}
 public static X509Certificate certificate(AdbIdentity identity,File dir)throws Exception{
  File file=new File(dir,"adb_tls_certificate.der");CertificateFactory factory=CertificateFactory.getInstance("X.509");
  if(file.isFile())try(InputStream in=new FileInputStream(file)){X509Certificate c=(X509Certificate)factory.generateCertificate(in);c.checkValidity();c.verify(identity.publicKey());if(c.getPublicKey().equals(identity.publicKey()))return c;}catch(Exception ignored){}
  byte[] algorithm=seq(der(6,new byte[]{0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,1,1,11}),der(5,new byte[0]));
  byte[] name=seq(der(0x31,seq(der(6,new byte[]{0x55,4,3}),der(12,"LinkDeck ADB".getBytes("UTF-8")))));
  SimpleDateFormat date=new SimpleDateFormat("yyMMddHHmmss'Z'",Locale.US);date.setTimeZone(TimeZone.getTimeZone("UTC"));long now=System.currentTimeMillis();byte[] validity=seq(der(23,date.format(new Date(now-86400000L)).getBytes("US-ASCII")),der(23,date.format(new Date(now+10L*365*86400000)).getBytes("US-ASCII")));
  byte[] serial=new BigInteger(120,new SecureRandom()).add(BigInteger.ONE).toByteArray();byte[] tbs=seq(der(0xa0,integer(new byte[]{2})),integer(serial),algorithm,name,validity,name,identity.publicKey().getEncoded());Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(identity.privateKey());signer.update(tbs);byte[] encoded=seq(tbs,algorithm,der(3,join(new byte[]{0},signer.sign())));
  X509Certificate cert=(X509Certificate)factory.generateCertificate(new ByteArrayInputStream(encoded));cert.verify(identity.publicKey());dir.mkdirs();File tmp=new File(dir,"adb_tls_certificate.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(encoded);out.getFD().sync();}if(!tmp.renameTo(file)){tmp.delete();throw new IOException("TLS certificate could not be saved");}return cert;
 }
 public static String fingerprint(PublicKey key)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(key.getEncoded());StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
}
