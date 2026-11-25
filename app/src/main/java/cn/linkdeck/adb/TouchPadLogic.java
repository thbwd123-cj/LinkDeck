package cn.linkdeck.adb;
/** 触摸板手势判定：纯逻辑，可 host 测试。距离单位 dp，调用方按屏幕密度换算成 px 再传入。 */
public final class TouchPadLogic {
  private TouchPadLogic(){}
  /** 滑动满这个距离发一个方向键。 */
  public static final float SWIPE_THRESHOLD_DP = 48f;
  /** 轻点/长按允许的最大位移。 */
  public static final float TAP_MAX_DP = 12f;
  /** 轻点最长按住时间。 */
  public static final long TAP_MAX_MS = 350;
  /** 长按判定时间。 */
  public static final long LONG_PRESS_MS = 500;
  /** 连续滑动时两次发键的最短间隔。 */
  public static final long REPEAT_MIN_MS = 110;
  /** 自上次发键起的累计位移 → 方向键码：19 上 / 20 下 / 21 左 / 22 右；不足阈值返回 0。优势轴取绝对值较大者。 */
  public static int direction(float dx, float dy, float thresholdPx) {
    float ax = Math.abs(dx), ay = Math.abs(dy);
    if (Math.max(ax, ay) < thresholdPx) return 0;
    if (ax >= ay) return dx > 0 ? 22 : 21;
    return dy > 0 ? 20 : 19;
  }
  /** 发键后保留的位移余量：沿优势方向减掉一个阈值，另一轴清零。返回 {rx, ry}。 */
  public static float[] consume(float dx, float dy, float thresholdPx) {
    float ax = Math.abs(dx), ay = Math.abs(dy);
    if (ax >= ay) return new float[]{dx - Math.signum(dx) * thresholdPx, 0f};
    return new float[]{0f, dy - Math.signum(dy) * thresholdPx};
  }
  /** 是否轻点：按住时间短且几乎没移动。 */
  public static boolean isTap(long durationMs, float movedPx, float tapMaxPx) {
    return durationMs < TAP_MAX_MS && movedPx < tapMaxPx;
  }
  /** 是否长按：按住够久且几乎没移动。 */
  public static boolean isLongPress(long durationMs, float movedPx, float tapMaxPx) {
    return durationMs >= LONG_PRESS_MS && movedPx < tapMaxPx;
  }
}
