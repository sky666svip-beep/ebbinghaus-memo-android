package android.util;

/**
 * 测试专用 `android.util.Log` 替身（仅存在于 `src/test` 源集，绝不进入 APK）。
 *
 * 背景：AGP 的 mockable-android.jar 中所有方法默认抛
 * `RuntimeException: Method e in android.util.Log not mocked`（本仓库未开启
 * `testOptions.unitTests.returnDefaultValues`，已实测确认）。这会让「加固 catch 分支里的
 * Log.e/Log.w」在纯 JVM 单测中直接抛异常，从而无法验证加固逻辑本身。
 *
 * 本替身把 Log 变为**可记录**的 no-op：
 * - 使 QA 能真实触发并断言加固的异常边界；
 * - 通过 [eCount] / [wCount] / [lastTag] 断言「加固确实写了日志（非静默失效）」；
 * - 通过断言 `eCount == 0` 证明 `CancellationException` 被原样抛出（未进入 catch(Exception)）。
 *
 * **不修改任何 app/src/main 业务源码，也不改动构建脚本**。
 * 之所以能生效，是因为单测 classpath 上测试源集输出目录优先于 mockable-android.jar。
 */
public final class Log {

    /** `e(...)` 被调用的次数（供测试断言「非静默」） */
    public static volatile int eCount;
    /** `w(...)` 被调用的次数 */
    public static volatile int wCount;
    /** 最近一次 `e/w` 的 tag */
    public static volatile String lastTag;
    /** 最近一次 `e/w` 的 message */
    public static volatile String lastMessage;
    /** 最近一次 `e/w` 携带的异常 */
    public static volatile Throwable lastThrowable;

    private Log() {
    }

    /** 测试辅助：清空记录（非 Android API，仅供测试调用） */
    public static void reset() {
        eCount = 0;
        wCount = 0;
        lastTag = null;
        lastMessage = null;
        lastThrowable = null;
    }

    private static int record(boolean isError, String tag, String msg, Throwable tr) {
        if (isError) {
            eCount++;
        } else {
            wCount++;
        }
        lastTag = tag;
        lastMessage = msg;
        lastThrowable = tr;
        return 0;
    }

    public static int v(String tag, String msg) {
        return 0;
    }

    public static int v(String tag, String msg, Throwable tr) {
        return 0;
    }

    public static int d(String tag, String msg) {
        return 0;
    }

    public static int d(String tag, String msg, Throwable tr) {
        return 0;
    }

    public static int i(String tag, String msg) {
        return 0;
    }

    public static int i(String tag, String msg, Throwable tr) {
        return 0;
    }

    public static int w(String tag, String msg) {
        return record(false, tag, msg, null);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return record(false, tag, msg, tr);
    }

    public static int w(String tag, Throwable tr) {
        return record(false, tag, null, tr);
    }

    public static int e(String tag, String msg) {
        return record(true, tag, msg, null);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return record(true, tag, msg, tr);
    }

    public static int wtf(String tag, String msg) {
        return record(true, tag, msg, null);
    }

    public static int wtf(String tag, String msg, Throwable tr) {
        return record(true, tag, msg, tr);
    }

    public static String getStackTraceString(Throwable tr) {
        return "";
    }

    public static boolean isLoggable(String tag, int level) {
        return false;
    }

    public static int println(int priority, String tag, String msg) {
        return 0;
    }
}
