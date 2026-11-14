package cn.linkdeck.adb;

import java.io.*;
import java.math.BigInteger;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import javax.crypto.Cipher;

/** Independent ADB authentication implementation. Keys never leave app-private storage. */
public final class AdbIdentity {
  private final PrivateKey privateKey;
  private final RSAPublicKey publicKey;

  public AdbIdentity(File dir) throws Exception {
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建 ADB 密钥目录");
    File pri = new File(dir, "adb_private.pk8"), pub = new File(dir, "adb_public.der");
    KeyFactory kf = KeyFactory.getInstance("RSA");
    PrivateKey restored = null;
    RSAPublicKey restoredPublic = null;
    if (pri.exists()) {
      try {
        restored = kf.generatePrivate(new PKCS8EncodedKeySpec(read(pri)));
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) restored;
        restoredPublic = (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
      } catch (Exception invalidPrivate) {
        // Never silently replace an authorized key: preserve both files and make reauthorization explicit.
        backup(pri); if (pub.exists()) backup(pub);
        generate(pri, pub);
        throw new IOException("ADB 身份密钥已损坏并重新生成，请重连电视并重新授权", invalidPrivate);
      }
      if (pub.exists()) {
        boolean valid = false;
        try {
          RSAPublicKey stored = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(read(pub)));
          valid = stored.getModulus().equals(restoredPublic.getModulus()) && stored.getPublicExponent().equals(restoredPublic.getPublicExponent());
        } catch (Exception invalidPublic) { /* The private key remains authoritative. */ }
        if (!valid) { backup(pub); atomicWrite(pub, restoredPublic.getEncoded()); }
      } else atomicWrite(pub, restoredPublic.getEncoded());
    } else if (pub.exists()) {
      backup(pub);
      generate(pri, pub);
      throw new IOException("ADB 私钥丢失，已生成新身份，请重连电视并重新授权");
    } else generate(pri, pub);
    if (restored != null) { privateKey = restored; publicKey = restoredPublic; }
    else {
      privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(read(pri)));
      publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(read(pub)));
    }
  }

  private static void generate(File pri, File pub) throws Exception {
      KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
      g.initialize(2048);
      KeyPair pair = g.generateKeyPair();
      atomicWrite(pri, pair.getPrivate().getEncoded());
      atomicWrite(pub, pair.getPublic().getEncoded());
  }

  private static void backup(File file) throws IOException {
    File target = new File(file.getParentFile(), file.getName()+".corrupt-"+System.currentTimeMillis());
    if (!file.renameTo(target)) throw new IOException("无法备份损坏的 ADB 密钥："+file.getName());
  }

  private static byte[] read(File f) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (InputStream in = new FileInputStream(f)) {
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
    return out.toByteArray();
  }

  private static void atomicWrite(File f, byte[] b) throws IOException {
    File temp = File.createTempFile("adb-key-", ".tmp", f.getParentFile());
    try {
      try (FileOutputStream o = new FileOutputStream(temp)) { o.write(b); o.getFD().sync(); }
      try { Files.move(temp.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
      catch (AtomicMoveNotSupportedException e) { Files.move(temp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    } finally { temp.delete(); }
  }

  PrivateKey privateKey(){return privateKey;}
  RSAPublicKey publicKey(){return publicKey;}

  public byte[] sign(byte[] token) throws Exception {
    if (token.length != 20) throw new IOException("Invalid ADB token length");
    byte[] prefix =
        new byte[] {
          0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        };
    byte[] message = new byte[35];
    System.arraycopy(prefix, 0, message, 0, 15);
    System.arraycopy(token, 0, message, 15, 20);
    Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    c.init(Cipher.ENCRYPT_MODE, privateKey);
    return c.doFinal(message);
  }

  private static void little(ByteBuffer out, BigInteger n) {
    for (int i = 0; i < 256; i++) {
      out.put(n.byteValue());
      n = n.shiftRight(8);
    }
  }

  public byte[] publicPayload() throws Exception {
    BigInteger n = publicKey.getModulus(), base = BigInteger.ONE.shiftLeft(32);
    ByteBuffer b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(64);
    b.putInt(n.modInverse(base).negate().intValue());
    little(b, n);
    little(b, BigInteger.ONE.shiftLeft(4096).mod(n));
    b.putInt(publicKey.getPublicExponent().intValue());
    return (base64(b.array()) + " linkdeck@android\0").getBytes("UTF-8");
  }

  private static String base64(byte[] a) {
    final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < a.length; i += 3) {
      int v = (a[i] & 255) << 16;
      if (i + 1 < a.length) v |= (a[i + 1] & 255) << 8;
      if (i + 2 < a.length) v |= a[i + 2] & 255;
      s.append(chars.charAt(v >>> 18))
          .append(chars.charAt((v >>> 12) & 63))
          .append(i + 1 < a.length ? chars.charAt((v >>> 6) & 63) : '=')
          .append(i + 2 < a.length ? chars.charAt(v & 63) : '=');
    }
    return s.toString();
  }
}
