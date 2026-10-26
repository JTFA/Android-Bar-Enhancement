package com.jtfa.barenhancement;

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

    /**
     * 模糊层同步模式固定为「均衡」档
     *
     * true（默认）：
     *  - 设置页隐藏「模糊层同步模式」整节（标题/说明/下拉框）
     *  - 运行时强制使用「均衡」档（忽略已保存的 blur_sync_mode；
     *    存储值不覆写，恢复开关后原保存档位原样生效）
     * false：设置页显示该项，按用户所选档位运行（0 精确同步 / 1 均衡 / 2 性能优先）
     */
    public static final boolean BLUR_SYNC_FORCE_BALANCED = true;

    /**
     * 「自定义 android.activity.windowingMode」输入框开关
     *
     * true ：在「尝试消除打开第三方应用授权弹窗」开关下方显示输入框，
     *        用户可输入整数 windowingMode（不输入默认 6），打开失败时
     *        自动回退普通 startActivity 并弹 toast 提示。
     * false（默认）：不显示该输入框，行为与不填该项相同（固定默认值 6）。
     */
    public static final boolean CUSTOM_WINDOWING_MODE_ENABLED = true;

    /**
     * 快捷窗口「位置Y」底边锚定高度（dp，等于最大高度设置的默认值 350）
     *
     * 快捷窗口底边位置 = 屏幕中心 + 2×positionY + 本常数/2，
     * 只依赖 positionY 与本常数，不随「快捷窗口最大高度」设置变化：
     *  - 调节最大高度 → 窗口不再上下移动（仅改变可增长上限/滚动阈值）
     *  - 图标行数增多 → 窗口向上增长、底边不动；超高后转滚动
     * 默认设置（350dp）下与历史行为完全一致。
     */
    public static final int DEFAULT_SHORTCUT_WINDOW_HEIGHT_DP = 350;

    /** 快捷方式图标列数：可调范围与默认值 */
    public static final int SHORTCUT_ICON_COLUMNS_MIN = 3;
    public static final int SHORTCUT_ICON_COLUMNS_MAX = 10;
    public static final int DEFAULT_SHORTCUT_ICON_COLUMNS = 4;

    private AppConfig() {
    }
}
