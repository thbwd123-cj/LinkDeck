package cn.linkdeck.adb;
import java.nio.*;import java.nio.charset.StandardCharsets;import java.util.*;import javax.crypto.*;import javax.crypto.spec.*;import io.github.muntashirakon.crypto.spake2.*;
/** Android ADB pairing_auth: SPAKE2, HKDF-SHA256 and AES-128-GCM counters. */
public final class PairingCrypto implements AutoCloseable {
 private final Spake2Context context;private final byte[] message;private byte[] key;private long enc,dec;
 public PairingCrypto(byte[] password,boolean client){byte[] a="adb pair client\0".getBytes(StandardCharsets.UTF_8),b="adb pair server\0".getBytes(StandardCharsets.UTF_8);context=new Spake2Context(client?Spake2Role.Alice:Spake2Role.Bob,client?a:b,client?b:a);message=context.generateMessage(password);}
 public byte[] message(){return message.clone();}
 public void finish(byte[] peer)throws Exception{byte[] material=context.processMessage(peer);if(material==null)throw new java.io.IOException("pairing key exchange failed");try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(new byte[32],"HmacSHA256"));byte[] prk=mac.doFinal(material);mac.init(new SecretKeySpec(prk,"HmacSHA256"));mac.update("adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.UTF_8));mac.update((byte)1);key=Arrays.copyOf(mac.doFinal(),16);Arrays.fill(prk,(byte)0);}finally{Arrays.fill(material,(byte)0);}}
 private byte[] crypt(byte[] data,boolean encrypt,long counter)throws Exception{if(key==null)throw new IllegalStateException("pairing cipher unavailable");byte[] iv=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(counter).array();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));return c.doFinal(data);}
 public byte[] encrypt(byte[] data)throws Exception{return crypt(data,true,enc++);}public byte[] decrypt(byte[] data)throws Exception{return crypt(data,false,dec++);}
 public void close(){if(key!=null)Arrays.fill(key,(byte)0);context.destroy();}
}
