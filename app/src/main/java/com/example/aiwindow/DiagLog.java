package com.example.aiwindow;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 诊断日志：把关键状态写入应用外部文件（diag.log）。
 * 由 {@link AppConfig#DIAG_LOG_ENABLED} 总开关控制，关闭时零 I/O 开销。
 */
public final class DiagLog {
    private static final String TAG = "DiagLog";
    private static final long MAX_BYTES = 512 * 1024;
    private static File sFile;

    private DiagLog() {
    }

    private static synchronized File file(Context ctx) {
        if (sFile == null) {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) {
                dir = ctx.getFilesDir();
            }
            sFile = new File(dir, "diag.log");
        }
        return sFile;
    }

    public static synchronized void log(Context ctx, String msg) {
        if (!AppConfig.DIAG_LOG_ENABLED) {
            return;
        }
        try {
            File f = file(ctx);
            if (f.exists() && f.length() > MAX_BYTES) {
                File old = new File(f.getParentFile(), "diag.old.log");
                //noinspection ResultOfMethodCallIgnored
                old.delete();
                //noinspection ResultOfMethodCallIgnored
                f.renameTo(old);
            }
            String line = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                    + " " + msg + "\n";
            FileOutputStream fos = new FileOutputStream(f, true);
            OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            w.write(line);
            w.close();
        } catch (Throwable t) {
            Log.w(TAG, "diag write failed: " + t);
        }
    }

    /** 安装未捕获异常处理器：崩溃时把堆栈写入日志文件（仅 DIAG_LOG_ENABLED=true 时生效） */
    public static void installUncaughtHandler(final Context ctx) {
        if (!AppConfig.DIAG_LOG_ENABLED) {
            return;
        }
        try {
            final Context app = ctx.getApplicationContext();
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    try {
                        StringBuilder sb = new StringBuilder();
                        sb.append("UNCAUGHT ").append(t.getName()).append(": ").append(e).append("\n");
                        StackTraceElement[] st = e.getStackTrace();
                        if (st != null) {
                            for (int i = 0; i < Math.min(st.length, 20); i++) {
                                sb.append("  at ").append(st[i]).append("\n");
                            }
                        }
                        log(app, sb.toString());
                    } catch (Throwable ignored) {
                    }
                    if (prev != null) {
                        prev.uncaughtException(t, e);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "install handler failed: " + t);
        }
    }
}
