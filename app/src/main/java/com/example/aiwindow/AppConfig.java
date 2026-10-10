package com.example.aiwindow;

/**
 * 应用功能总配置
 */
public final class AppConfig {

    /**
     * AI 窗口功能总开关
     *
     * false（当前）：
     *  - 设置页隐藏「窗口设置」「AI 窗口模糊效果（含输入法避让）」两张卡片
     *    （「点击窗口外关闭」已移入背景遮罩卡片，保持可见）
     *  - 手势行为下拉框隐藏「打开AI窗口」选项，默认长按行为回退为「无」，
     *    已保存的 ai 行为在运行时按「无」处理（存储值不覆写，恢复开关后原样生效）
     *  - 权限管理页仅显示悬浮窗，不再运行时申请麦克风/相机权限
     *  - AndroidManifest.xml 中联网/麦克风/相机/存储相关权限声明以注释形式保留
     *
     * 恢复功能：把本常量改为 true，并取消 AndroidManifest.xml 中对应权限的注释，重新编译即可。
     * 功能实现代码全部保留，未删除。
     */
    public static final boolean AI_WINDOW_ENABLED = false;

    /**
     * 诊断日志开关
     *
     * true ：把关键状态（设置变更、横条触摸、光晕挂载/拆卸、服务生命周期、崩溃堆栈）
     *        写入应用外部文件 diag.log。
     * false（默认）：完全不写日志、不安装崩溃捕获，调用点仅一次布尔判断，零 I/O 开销。
     */
    public static final boolean DIAG_LOG_ENABLED = false;

    private AppConfig() {
    }
}
