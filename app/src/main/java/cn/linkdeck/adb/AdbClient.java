package cn.linkdeck.adb;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Persistent multiplexed ADB transport; no original APK code or binaries used. */
public final class AdbClient implements Closeable {
  static final int CNXN = code("CNXN"),
      AUTH = code("AUTH"),
      OPEN = code("OPEN"),
      OKAY = code("OKAY"),
      WRTE = code("WRTE"),
      CLSE = code("CLSE");
  public interface TlsUpgrade {Socket upgrade(Socket plain,String host,int port,AdbIdentity identity)throws Exception;default void verify(Socket established)throws Exception{}}
  private final TlsUpgrade tlsUpgrade;
  private boolean tlsActive;
  public boolean isSecure(){return tlsActive;}
  public AdbClient(){this(null);}
  public AdbClient(TlsUpgrade upgrade){tlsUpgrade=upgrade;}
  private volatile Socket socket = new Socket();
  private InputStream input;
  private OutputStream output;
  private volatile boolean connected;
  private int maxData = 4096;
  private long handshakeDeadline;

  public interface ConnectionProgress {
    void update(String stage);
  }

  public static final class ConnectionFailure extends IOException {
    ConnectionFailure(String message, Exception cause) {
      super(message, cause);
    }
  }

  private final AtomicInteger ids = new AtomicInteger(1);
  private final ConcurrentHashMap<Integer, Channel> channels = new ConcurrentHashMap<>();

  public static int code(String s) {
    return s.charAt(0) | (s.charAt(1) << 8) | (s.charAt(2) << 16) | (s.charAt(3) << 24);
  }

  static int integer(byte[] b, int p) {
    return (b[p] & 255) | ((b[p + 1] & 255) << 8) | ((b[p + 2] & 255) << 16) | (b[p + 3] << 24);
  }

  public static String quote(String s) {
    return "'" + s.replace("'", "'\\''") + "'";
  }

  public void connect(String host, int port, AdbIdentity identity) throws Exception {
    connect(host, port, identity, stage -> {});
  }

  public void connect(String host, int port, AdbIdentity identity, ConnectionProgress progress)
      throws Exception {
    String stage = "网络连接";
    boolean awaitingAuthorization = false;
    try {
      progress.update("正在连接 " + host + ":" + port + "（最多 5 秒）");
      socket.connect(new InetSocketAddress(host, port), 5000);
      socket.setTcpNoDelay(true);
      socket.setKeepAlive(true);
      stage = "ADB 握手";
      progress.update("端口已连通，正在等待电视 ADB 响应…");
      handshakeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
      input = socket.getInputStream();
      output = socket.getOutputStream();
      send(CNXN, 0x01000000, 4096, "host::\0".getBytes("UTF-8"));
      int attempts = 0;
      for (; ; ) {
        Packet p = receive();
        if (p.command == CNXN) {
          if(tlsUpgrade!=null&&!tlsActive)throw new IOException("此端口返回未加密 ADB，未按加密模式连接；请使用无线调试的连接端口");
          if (p.arg1 <= 0) throw new IOException("设备返回无效的数据包长度");
          if(tlsActive)tlsUpgrade.verify(socket);
          maxData = Math.min(p.arg1, 256 * 1024);
          break;
        }
        if (p.command == AUTH && p.arg0 == 1) {
          if(tlsUpgrade!=null)throw new IOException("此端口使用传统授权，不是加密无线连接端口");
          stage = "电视授权";
          awaitingAuthorization = true;
          progress.update("正在验证授权；请查看电视并允许此手机调试（总等待最多 60 秒）");
          send(
              AUTH,
              attempts++ == 0 ? 2 : 3,
              0,
              attempts == 1 ? identity.sign(p.data) : identity.publicPayload());
          if (attempts > 3) throw new IOException("电视未接受授权，请在电视上允许调试后重试");
        } else if (p.command == code("STLS")) {
          if(tlsUpgrade==null)throw new IOException("此端口要求加密无线调试，请通过连接向导先配对，再使用连接端口。");
          stage="加密身份核对";progress.update("正在核对加密设备身份…");send(code("STLS"),0x01000000,0,new byte[0]);socket=tlsUpgrade.upgrade(socket,host,port,identity);tlsActive=true;input=socket.getInputStream();output=socket.getOutputStream();
        } else throw new IOException("该端口没有返回兼容的 ADB 协议，请核对电视调试端口");
      }
    } catch (Exception e) {
      close();
      String reason;
      if (e instanceof SocketTimeoutException) {
        reason =
            stage.equals("网络连接")
                ? "无法连通电视端口。请核对当前 IP、端口、手机与电视网络，以及路由器隔离设置。"
                : awaitingAuthorization
                    ? "电视授权等待超时。请查看电视授权弹窗；允许后再连接。没有弹窗时可尝试关闭再开启电视 ADB 调试。"
                    : "端口已连通，但电视没有完成 ADB 握手。请核对端口并重新开启电视 ADB 调试。";
      } else if (e instanceof ConnectException) {
        reason = "电视拒绝连接，或端口不可达。请确认已开启网络 ADB 调试，并核对实际端口。";
      } else if (e instanceof UnknownHostException) {
        reason = "无法解析电视地址。请填写电视网络设置里显示的当前 IP。";
      } else reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      throw new ConnectionFailure(
          "目标："
              + host
              + ":"
              + port
              + "\n阶段："
              + stage
              + "\n\n"
              + reason
              + "\n\n原始信息："
              + e.toString(),
          e);
    }
    handshakeDeadline = 0;
    socket.setSoTimeout(0);
    connected = true;
    Thread reader =
        new Thread(
            () -> {
              try {
                while (connected) {
                  Packet p = receive();
                  Channel ch = channels.get(p.arg1);
                  if (ch == null) continue;
                  if (p.command == OKAY) {
                    ch.remote = p.arg0;
                    ch.acks.offer(Boolean.TRUE);
                  } else if (p.command == WRTE) {
                    ch.remote = p.arg0;
                    ch.parts.offer(p.data);
                    send(OKAY, ch.local, ch.remote, new byte[0]);
                  } else if (p.command == CLSE) {
                    ch.remote = p.arg0;
                    ch.ended = true;
                    ch.parts.offer(new byte[0]);
                    ch.acks.offer(Boolean.FALSE);
                    try {
                      send(CLSE, ch.local, ch.remote, new byte[0]);
                    } finally {
                      channels.remove(ch.local);
                    }
                  }
                }
              } catch (Exception e) {
                close();
              }
            },
            "linkdeck-adb-reader");
    reader.setDaemon(true);
    reader.start();
  }

  public boolean isConnected() {
    return connected && !socket.isClosed();
  }

  private static final class Packet {
    int command, arg0, arg1;
    byte[] data;
  }

  private Packet receive() throws IOException {
    byte[] h = new byte[24];
    readPacketBytes(h);
    Packet p = new Packet();
    p.command = integer(h, 0);
    p.arg0 = integer(h, 4);
    p.arg1 = integer(h, 8);
    int n = integer(h, 12);
    if (integer(h, 20) != (p.command ^ (-1)) || n < 0 || n > 1024 * 1024)
      throw new IOException("无效的 ADB 数据包");
    p.data = new byte[n];
    readPacketBytes(p.data);
    int sum = 0;
    for (byte x : p.data) sum += x & 255;
    if (integer(h, 16) != sum) throw new IOException("ADB 校验失败");
    return p;
  }

  private void readPacketBytes(byte[] bytes) throws IOException {
    int offset = 0;
    while (offset < bytes.length) {
      if (handshakeDeadline != 0) {
        long remaining = handshakeDeadline - System.nanoTime();
        if (remaining <= 0) throw new SocketTimeoutException("ADB 握手等待超时");
        socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
      }
      int count = input.read(bytes, offset, bytes.length - offset);
      if (count < 0) throw new EOFException("电视关闭了 ADB 连接");
      offset += count;
    }
  }

  private synchronized void send(int cmd, int a, int b, byte[] data) throws IOException {
    int sum = 0;
    for (byte x : data) sum += x & 255;
    ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
    h.putInt(cmd).putInt(a).putInt(b).putInt(data.length).putInt(sum).putInt(cmd ^ (-1));
    output.write(h.array());
    output.write(data);
    output.flush();
  }

  public Channel open(String service) throws Exception {
    return open(service, 30000);
  }

  private Channel open(String service, long timeout) throws Exception {
    if (!isConnected()) throw new IOException("设备已断开，请重新连接");
    Channel c = new Channel(ids.getAndIncrement());
    c.timeout = timeout;
    channels.put(c.local, c);
    try {
      send(OPEN, c.local, 0, (service + "\0").getBytes("UTF-8"));
      c.awaitAck();
      return c;
    } catch (Exception e) {
      c.close();
      throw e;
    }
  }

  public final class Channel implements Closeable {
    final int local;
    volatile int remote;
    volatile boolean ended;
    long timeout = 30000;
    final BlockingQueue<byte[]> parts = new LinkedBlockingQueue<>();
    final BlockingQueue<Boolean> acks = new LinkedBlockingQueue<>();
    byte[] current;
    int position;

    Channel(int id) {
      local = id;
    }

    void awaitAck() throws Exception {
      Boolean ok = acks.poll(timeout, TimeUnit.MILLISECONDS);
      if (ok == null) throw new SocketTimeoutException("设备响应超时");
      if (!ok) throw new IOException("设备关闭了操作通道");
    }

    public synchronized void write(byte[] b) throws Exception {
      for (int p = 0; p < b.length; p += maxData) {
        if (ended) throw new IOException("通道已关闭");
        send(WRTE, local, remote, Arrays.copyOfRange(b, p, Math.min(b.length, p + maxData)));
        awaitAck();
      }
    }

    public int read(byte[] b, int off, int len) throws Exception {
      if (len == 0) return 0;
      while (current == null || position == current.length) {
        if (ended && parts.isEmpty()) return -1;
        current = parts.poll(timeout, TimeUnit.MILLISECONDS);
        position = 0;
        if (current == null) throw new SocketTimeoutException("设备响应超时");
        if (current.length == 0) return -1;
      }
      int n = Math.min(len, current.length - position);
      System.arraycopy(current, position, b, off, n);
      position += n;
      return n;
    }

    public void exact(byte[] b) throws Exception {
      int p = 0;
      while (p < b.length) {
        int n = read(b, p, b.length - p);
        if (n < 0) throw new EOFException("文件传输被中断");
        p += n;
      }
    }

    public void close() {
      boolean was = ended;
      ended = true;
      channels.remove(local);
      parts.offer(new byte[0]);
      acks.offer(Boolean.FALSE);
      if (!was)
        try {
          send(CLSE, local, remote, new byte[0]);
        } catch (Exception ignored) {
        }
    }
  }

  public static final class Result {
    public final String text, raw;
    public final int exitCode;

    Result(String t, int c) {
      raw = t;
      text = t.trim();
      exitCode = c;
    }

    public String require() throws IOException {
      if (exitCode != 0)
        throw new IOException(text.length() == 0 ? "命令执行失败（" + exitCode + "）" : text);
      return text;
    }
  }

  public Result shell(String command) throws Exception {
    return shell(command, 30000);
  }

  /** A command may have taken effect even when transport completion is unavailable. */
  public static final class ShellFailure extends IOException {
    public final String raw, stage;
    ShellFailure(String message, String output, String phase, Throwable cause) {
      super(message, cause); raw=output; stage=phase;
    }
  }

  private static String shellText(ByteArrayOutputStream out) throws IOException {
    return out.toString("UTF-8").replace("\r\r\n", "\n").replace("\r\n", "\n").replace('\r', '\n');
  }

  public Result shell(String command, long timeout) throws Exception {
    String tag = "__LINKDECK_" + UUID.randomUUID().toString().replace("-", "") + "__";
    String wrapped = "( " + command + "\n); __ld_status=$?; echo; echo " + tag + "$__ld_status";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    String phase="等待电视接受命令通道";
    long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.max(1,timeout));
    try (Channel c = open("shell:" + wrapped, timeout)) {
      phase="等待命令输出或完成状态";
      byte[] b = new byte[8192];
      while (true) {
        long remaining=deadline-System.nanoTime();
        if(remaining<=0)throw new SocketTimeoutException("命令等待超时");
        c.timeout=Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining));
        int n=c.read(b,0,b.length);
        if(n==-1)break;
        out.write(b,0,n);
        if(out.size()>8*1024*1024)throw new IOException("输出超过 8 MB，请缩小查询范围");
      }
      String text=shellText(out);
      int at=text.lastIndexOf("\n"+tag);
      String tail=at<0?"":text.substring(at+tag.length()+1).trim();
      if(at<0||!tail.matches("[0-9]{1,3}"))
        throw new ShellFailure("ADB 已建立，但未收到有效的命令完成状态，不能确认操作是否成功。",text,phase,null);
      return new Result(text.substring(0,at),Integer.parseInt(tail));
    } catch(ShellFailure e) {throw e;
    } catch(IOException e) {
      String reason=e instanceof SocketTimeoutException
          ?"命令等待超时（"+phase+"）。命令可能已经执行，结果尚未确认；请勿直接重复执行会修改设置的命令。"
          :"命令结果未确认（"+phase+"）："+e.getMessage();
      throw new ShellFailure(reason,shellText(out),phase,e);
    }
  }

  public byte[] screenshot() throws Exception {
    try (Channel c = open("exec:screencap -p")) {
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      byte[] b = new byte[8192];
      int n;
      while ((n = c.read(b, 0, b.length)) != -1) {
        o.write(b, 0, n);
        if (o.size() > 24 * 1024 * 1024) throw new IOException("截图过大");
      }
      return o.toByteArray();
    }
  }

  private static byte[] record(String id, byte[] data) {
    ByteBuffer b = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(code(id)).putInt(data.length).put(data);
    return b.array();
  }

  private static byte[] header(String id, int n) {
    return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(code(id)).putInt(n).array();
  }

  public interface Progress {
    void update(long bytes);
  }

  private static String failure(Channel c, int n) throws Exception {
    if (n < 0 || n > 65536) throw new IOException("无效的传输错误");
    byte[] b = new byte[n];
    c.exact(b);
    return new String(b, "UTF-8");
  }

  public void push(InputStream source, String path, Progress progress) throws Exception {
    try (Channel c = open("sync:")) {
      c.write(record("SEND", (path + ",33206").getBytes("UTF-8")));
      byte[] buf = new byte[32768];
      int n;
      long total = 0;
      while ((n = source.read(buf)) != -1) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("传输已取消");
        c.write(record("DATA", Arrays.copyOf(buf, n)));
        total += n;
        if (progress != null) progress.update(total);
      }
      c.write(header("DONE", (int) (System.currentTimeMillis() / 1000)));
      byte[] h = new byte[8];
      c.exact(h);
      if (integer(h, 0) == code("FAIL")) throw new IOException(failure(c, integer(h, 4)));
      if (integer(h, 0) != OKAY) throw new IOException("未收到上传成功状态");
    }
  }

  public void pull(String path, OutputStream destination, Progress progress) throws Exception {
    try (Channel c = open("sync:")) {
      c.write(record("RECV", path.getBytes("UTF-8")));
      long total = 0;
      byte[] h = new byte[8];
      for (; ; ) {
        c.exact(h);
        int id = integer(h, 0), n = integer(h, 4);
        if (id == code("DONE")) break;
        if (id == code("FAIL")) throw new IOException(failure(c, n));
        if (id != code("DATA") || n < 0 || n > 65536) throw new IOException("无效的文件数据");
        byte[] b = new byte[n];
        c.exact(b);
        destination.write(b);
        total += n;
        if (progress != null) progress.update(total);
      }
    }
  }

  public static final class Entry {
    public String name;
    public long size, mtime;
    public int mode;

    public boolean directory() {
      return (mode & 0170000) == 0040000;
    }
  }

  public List<Entry> list(String path) throws Exception {
    List<Entry> items = new ArrayList<>();
    try (Channel c = open("sync:")) {
      c.write(record("LIST", path.getBytes("UTF-8")));
      byte[] head = new byte[4];
      for (; ; ) {
        c.exact(head);
        int id = integer(head, 0);
        if (id == code("FAIL")) {
          byte[] l = new byte[4];
          c.exact(l);
          throw new IOException(failure(c, integer(l, 0)));
        }
        if (id == code("DONE")) {
          byte[] rest = new byte[16];
          c.exact(rest);
          break;
        }
        if (id != code("DENT")) throw new IOException("无法读取文件目录");
        byte[] meta = new byte[16];
        c.exact(meta);
        int len = integer(meta, 12);
        if (len < 0 || len > 65536) throw new IOException("无效文件名");
        byte[] name = new byte[len];
        c.exact(name);
        Entry e = new Entry();
        e.mode = integer(meta, 0);
        e.size = integer(meta, 4) & 0xffffffffL;
        e.mtime = integer(meta, 8) & 0xffffffffL;
        e.name = new String(name, "UTF-8");
        if (!e.name.equals(".") && !e.name.equals("..")) items.add(e);
      }
    }
    Collections.sort(
        items,
        (a, b) ->
            a.directory() != b.directory()
                ? (a.directory() ? -1 : 1)
                : a.name.compareToIgnoreCase(b.name));
    return items;
  }

  @Override
  public void close() {
    connected = false;
    try {
      socket.close();
    } catch (Exception ignored) {
    }
    for (Channel c : channels.values()) {
      c.ended = true;
      c.parts.offer(new byte[0]);
      c.acks.offer(Boolean.FALSE);
    }
    channels.clear();
  }
}
