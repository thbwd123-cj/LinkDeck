package cn.linkdeck.adb;
import java.io.*;import java.net.*;import java.security.*;import java.security.cert.X509Certificate;import java.util.*;import javax.net.ssl.*;import org.conscrypt.Conscrypt;
/** Pairing and TLS transport. Secrets are never returned as exception text or written to logs. */
public final class WirelessAdb implements Closeable {
 private volatile Socket socket;private volatile boolean cancelled;
 public synchronized void cancel(){cancelled=true;close();}
 private synchronized void attach(Socket next)throws IOException{if(cancelled){next.close();throw new IOException("配对已取消");}socket=next;}
 public boolean cancelled(){return cancelled;}
 public static final class ApprovalRequired extends IOException {public final String fingerprint;public ApprovalRequired(String pin){super("首次加密连接需要核对设备身份");fingerprint=pin;}}
 public static final class PinMismatch extends IOException {public PinMismatch(){super("设备加密身份已变化。已停止连接，请核对电视后重新配对；不会自动信任新身份。");}}
 public static SSLContext context(AdbIdentity identity,File dir)throws Exception{
  final X509Certificate cert=TlsIdentity.certificate(identity,dir);final PrivateKey key=identity.privateKey();
  X509ExtendedKeyManager km=new X509ExtendedKeyManager(){public String[] getClientAliases(String t,Principal[] i){return new String[]{"adb"};}public String chooseClientAlias(String[] t,Principal[] i,Socket s){return "adb";}public String[] getServerAliases(String t,Principal[] i){return new String[]{"adb"};}public String chooseServerAlias(String t,Principal[] i,Socket s){return "adb";}public PrivateKey getPrivateKey(String a){return key;}public X509Certificate[] getCertificateChain(String a){return new X509Certificate[]{cert};}};
  // ADB uses self-signed certificates. Pairing authenticates via SPAKE2 + TLS exporter;
  // command connections require explicit first-use approval and a persisted public-key pin.
  X509TrustManager trust=new X509TrustManager(){public void checkClientTrusted(X509Certificate[] c,String a){}public void checkServerTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{if(c==null||c.length==0)throw new java.security.cert.CertificateException("missing peer certificate");}public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}};
  SSLContext ctx=SSLContext.getInstance("TLSv1.3",Conscrypt.newProvider());ctx.init(new KeyManager[]{km},new TrustManager[]{trust},new SecureRandom());return ctx;
 }
 public static AdbClient.TlsUpgrade transport(File dir,String expectedPin){return new AdbClient.TlsUpgrade(){
  public Socket upgrade(Socket plain,String host,int port,AdbIdentity identity)throws Exception{return handshake(plain,host,port,identity,dir);}
  // Wait for the server's CNXN before rejecting/asking for trust: older adbd can crash
  // if TLS is closed between its handshake and asynchronous authorization callback.
  // AdbClient has not marked the transport connected and cannot send OPEN commands yet.
  public void verify(Socket established)throws Exception{SSLSocket tls=(SSLSocket)established;String pin=TlsIdentity.fingerprint(tls.getSession().getPeerCertificates()[0].getPublicKey());if(expectedPin==null||expectedPin.isEmpty())throw new ApprovalRequired(pin);if(!MessageDigest.isEqual(expectedPin.getBytes("US-ASCII"),pin.getBytes("US-ASCII")))throw new PinMismatch();}
 };}
 private static SSLSocket handshake(Socket plain,String host,int port,AdbIdentity identity,File dir)throws Exception{
  SSLSocket tls=(SSLSocket)context(identity,dir).getSocketFactory().createSocket(plain,host,port,true);tls.setUseClientMode(true);tls.setEnabledProtocols(new String[]{"TLSv1.3"});tls.setSoTimeout(15000);
  try{tls.startHandshake();return tls;}catch(Exception e){try{tls.close();}catch(Exception ignored){}throw e;}
 }
 private static byte[] read(DataInputStream in,int type)throws IOException{int version=in.readUnsignedByte(),actual=in.readUnsignedByte(),length=in.readInt();if(version!=1||actual!=type||length<1||length>16384)throw new IOException("配对响应格式不兼容");byte[] b=new byte[length];in.readFully(b);return b;}
 private static void write(DataOutputStream out,int type,byte[] data)throws IOException{out.writeByte(1);out.writeByte(type);out.writeInt(data.length);out.write(data);out.flush();}
 public String pair(String host,int port,char[] code,AdbIdentity identity,File dir)throws Exception{
  byte[] password=null,exported=null;
  try{if(cancelled)throw new IOException("配对已取消");
  if(code==null||code.length!=6)throw new IOException("请输入电视当前显示的六位配对码");for(char c:code)if(c<'0'||c>'9')throw new IOException("配对码必须为六位数字");
  attach(new Socket());socket.connect(new InetSocketAddress(host,port),5000);socket.setSoTimeout(15000);SSLSocket tls=(SSLSocket)context(identity,dir).getSocketFactory().createSocket(socket,host,port,true);attach(tls);tls.setUseClientMode(true);tls.setEnabledProtocols(new String[]{"TLSv1.3"});tls.startHandshake();exported=Conscrypt.exportKeyingMaterial(tls,"adb-label\0",null,64);if(exported==null)throw new IOException("TLS 密钥导出失败");password=new byte[6+exported.length];for(int i=0;i<6;i++)password[i]=(byte)code[i];System.arraycopy(exported,0,password,6,exported.length);
   try(PairingCrypto crypto=new PairingCrypto(password,true)){DataInputStream in=new DataInputStream(tls.getInputStream());DataOutputStream out=new DataOutputStream(tls.getOutputStream());write(out,0,crypto.message());crypto.finish(read(in,0));byte[] info=new byte[8192];byte[] publicKey=identity.publicPayload();if(publicKey.length>8191)throw new IOException("配对公钥长度无效");info[0]=0;System.arraycopy(publicKey,0,info,1,publicKey.length);write(out,1,crypto.encrypt(info));byte[] peer=crypto.decrypt(read(in,1));if(peer.length!=8192||peer[0]!=1)throw new IOException("配对设备身份响应无效");int end=1;while(end<peer.length&&peer[end]!=0)end++;String guid=new String(peer,1,end-1,"UTF-8");if(guid.isEmpty()||guid.length()>128)throw new IOException("配对设备身份无效");if(cancelled)throw new IOException("配对已取消");return guid;}
  }catch(SocketTimeoutException e){throw new IOException("配对等待超时，请保持电视配对码窗口打开，并核对配对端口");}catch(IOException e){throw e;}catch(Exception e){throw new IOException("配对未完成：配对码可能已过期、端口不符或设备不兼容，请重新核对后再试");}finally{if(code!=null)Arrays.fill(code,'\0');if(password!=null)Arrays.fill(password,(byte)0);if(exported!=null)Arrays.fill(exported,(byte)0);close();}
 }
 public synchronized void close(){Socket s=socket;socket=null;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
