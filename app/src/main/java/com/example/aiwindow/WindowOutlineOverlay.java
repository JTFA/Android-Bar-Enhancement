package com.example.aiwindow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;

/**
 * 设置页内的「快捷窗口轮廓」实时预览层。
 *
 * 叠加方式：MATCH_PARENT 添加到 SettingsActivity 的 android.R.id.content 顶层，
 * 无需悬浮窗权限，不影响设置页既有 View 层级。
 *
 * 触摸穿透：不设 background、不可点击、onTouchEvent 恒返回 false，
 * 事件全部落到下层设置列表，不影响用户拖滑块等手部操作。
 *
 * 轮廓几何与快捷窗口真实锚定公式一致（见 OverlayWindowActivity.addShortcutContent）：
 *   bottom = 屏幕中心 + 2×positionY + 锚定K/2（K = 默认最大高度 350dp）
 * 注意单位：宽度/高度/圆角设置值是 dp（实际窗口经 dpToPx 转换），
 * 而「位置Y」滑块值在实际窗口中全程按像素(px)消费（无 dpToPx，
 * 见 OverlayWindowActivity.parseIntent / FloatWindowService.launchShortcutWindow），
 * 预览必须同样按 px 使用，否则偏差达 density 倍。
 * 高度按「最大高度」设置值绘制；实际高度以图标行数为准（由提示文案说明）。
 */
public class WindowOutlineOverlay extends View {

    private static final String HINT_TEXT = "实际高度以图标行数为准";
    private static final long HIDE_DELAY_MS = 1000;  // 松手后延迟渐隐
    private static final long HIDE_ANIM_MS = 300;

    // 流光彩虹边框：一个渐变周期的像素长度与流动一圈的时长
    private static final float FLOW_PERIOD_PX = 900f;
    private static final long FLOW_DURATION_MS = 1600;
    // 首尾同色，配合 TileMode.REPEAT 无缝循环
    private static final int[] RAINBOW_COLORS = {
            0xFFFF0040, 0xFFFF8800, 0xFFFFE000, 0xFF44FF00,
            0xFF00FFD0, 0xFF00A6FF, 0xFF4050FF, 0xFFD000FF,
            0xFFFF0040
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            animate().alpha(0f).setDuration(HIDE_ANIM_MS)
                    .withEndAction(WindowOutlineOverlay.this::stopFlow)
                    .start();
        }
    };

    // 当前预览参数：width/height/corner 为 dp（绘制时乘 density）；
    // posY 为滑块原始值，实际窗口按像素(px)消费，绘制时不乘 density
    private int widthDp;
    private int heightDp;
    private int posYValue;
    private int cornerDp;

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Matrix flowMatrix = new Matrix();
    private ValueAnimator flowAnimator;

    public WindowOutlineOverlay(Context context) {
        this(context, null);
    }

    public WindowOutlineOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f * density);
        // 流光彩虹边框：横向重复渐变，流动由 flowAnimator 平移 shader 矩阵实现
        strokePaint.setShader(new LinearGradient(0f, 0f, FLOW_PERIOD_PX, 0f,
                RAINBOW_COLORS, null, Shader.TileMode.REPEAT));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0x1AFFFFFF);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(13f * scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);

        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(0xB0000000);

        setAlpha(0f);
    }

    /** 更新轮廓参数并重绘（width/height/corner 为 dp，posY 为滑块原始值=px） */
    public void update(int widthDp, int heightDp, int posYValue, int cornerDp) {
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        this.posYValue = posYValue;
        this.cornerDp = cornerDp;
        invalidate();
    }

    /** 立即显示（取消排队的渐隐），并启动彩虹流动 */
    public void show() {
        handler.removeCallbacks(hideRunnable);
        animate().cancel();  // 先取消，避免其结束回调把刚启动的流动停掉
        startFlow();
        if (getAlpha() < 1f) {
            animate().alpha(1f).setDuration(120).start();
        }
    }

    /** 松手后延迟渐隐，渐隐结束后停止彩虹流动 */
    public void scheduleHide() {
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, HIDE_DELAY_MS);
    }

    /** 立即隐藏（无动画），用于离开页面等场景 */
    public void hideNow() {
        handler.removeCallbacks(hideRunnable);
        animate().cancel();
        setAlpha(0f);
        stopFlow();
    }

    /** 启动边框彩虹流动（已在运行则不重复启动） */
    private void startFlow() {
        if (flowAnimator != null && flowAnimator.isRunning()) return;
        if (flowAnimator == null) {
            flowAnimator = ValueAnimator.ofFloat(0f, FLOW_PERIOD_PX);
            flowAnimator.setDuration(FLOW_DURATION_MS);
            flowAnimator.setInterpolator(new LinearInterpolator());
            flowAnimator.setRepeatCount(ValueAnimator.INFINITE);
            flowAnimator.setRepeatMode(ValueAnimator.RESTART);
            flowAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    Shader shader = strokePaint.getShader();
                    if (shader != null) {
                        flowMatrix.setTranslate(-(float) animation.getAnimatedValue(), 0f);
                        shader.setLocalMatrix(flowMatrix);
                    }
                    invalidate();
                }
            });
        }
        flowAnimator.start();
    }

    /** 停止边框彩虹流动 */
    private void stopFlow() {
        if (flowAnimator != null) flowAnimator.cancel();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 恒不消费：事件穿透到下层设置列表；触发无障碍点击播报所需的回调
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return false;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (widthDp <= 0 || heightDp <= 0 || getAlpha() <= 0.01f) return;

        float density = getResources().getDisplayMetrics().density;
        float w = widthDp * density;
        float h = heightDp * density;
        float corner = Math.min(cornerDp * density, Math.min(w, h) / 2f);

        // 窗口中心 = 真实屏幕中心（与 FloatWindowService 展开路径一致），
        // 换算到本 View 坐标系（本 View 可能因状态栏等与屏幕不完全对齐）
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        android.view.WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        android.util.DisplayMetrics real = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(real);
        float centerX = real.widthPixels / 2f - loc[0];
        float centerY = real.heightPixels / 2f - loc[1];

        // 底边 = 屏幕中心 + 2×positionY + 锚定K/2（K = 默认最大高度 350dp）
        // positionY 按 px 与实际窗口一致，不乘 density
        float bottom = centerY
                + 2f * posYValue
                + AppConfig.DEFAULT_SHORTCUT_WINDOW_HEIGHT_DP * density / 2f;
        float top = bottom - h;
        float left = centerX - w / 2f;

        RectF rect = new RectF(left, top, left + w, bottom);
        canvas.drawRoundRect(rect, corner, corner, fillPaint);
        canvas.drawRoundRect(rect, corner, corner, strokePaint);
        drawHint(canvas, top, density);
    }

    /** 轮廓上方的胶囊提示；顶部空间不足时放轮廓内，并钳入屏幕 */
    private void drawHint(Canvas canvas, float rectTop, float density) {
        float pillH = 24f * density;
        float padH = 10f * density;
        float textW = textPaint.measureText(HINT_TEXT);
        float pillW = textW + padH * 2f;
        float cx = getWidth() / 2f;

        float pillTop = rectTop - 8f * density - pillH;
        pillTop = Math.max(4f * density,
                Math.min(pillTop, getHeight() - pillH - 4f * density));

        RectF pill = new RectF(cx - pillW / 2f, pillTop, cx + pillW / 2f, pillTop + pillH);
        canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, pillPaint);

        float textBaseline = pillTop + pillH / 2f
                - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(HINT_TEXT, cx, textBaseline, textPaint);
    }
}
