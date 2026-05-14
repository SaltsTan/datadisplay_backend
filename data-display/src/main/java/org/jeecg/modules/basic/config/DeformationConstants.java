package org.jeecg.modules.basic.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 排体变形可视化 — 物理常量与算法参数配置
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
public final class DeformationConstants {

    private DeformationConstants() {}

    // ==================== 物理尺寸常量 ====================

    /** 软体排全长 (m) */
    public static final double RAFT_LENGTH = 200.0;

    /** 软体排全宽 (m) */
    public static final double RAFT_WIDTH = 40.0;

    /** 大U左侧 Y 坐标 */
    public static final double BIG_U_LEFT_Y = 10.0;

    /** 大U右侧 Y 坐标 */
    public static final double BIG_U_RIGHT_Y = 29.0;

    /** 小U左侧 Y 坐标 */
    public static final double SMALL_U_LEFT_Y = 14.0;

    /** 小U右侧 Y 坐标 */
    public static final double SMALL_U_RIGHT_Y = 23.0;

    // ==================== 光缆长度分组 ====================

    /** A组排体号: 大U=195m, 小U=90m */
    public static final Set<String> GROUP_195_90 = new HashSet<>(
            Arrays.asList("19", "20", "24", "25"));

    /** B组排体号: 大U=180m, 小U=70m */
    public static final Set<String> GROUP_180_70 = new HashSet<>(
            Arrays.asList("21", "22", "23"));

    /** A组大U长度 */
    public static final int LARGE_LEN_195 = 195;

    /** A组小U长度 */
    public static final int SMALL_LEN_90 = 90;

    /** B组大U长度 */
    public static final int LARGE_LEN_180 = 180;

    /** B组小U长度 */
    public static final int SMALL_LEN_70 = 70;

    /** #25排体大U左侧完好传感器数（用于补充右侧断裂） */
    public static final int P25_LEFT_COUNT = 195;

    /** #25排体大U右侧断裂后剩余传感器数 */
    public static final int P25_RIGHT_COUNT = 111;

    // ==================== 曲面插值网格参数 ====================

    /** X轴网格点数 (步长 1m，共 201 个采样点) */
    public static final int GRID_X_STEPS = 201;

    /** Y轴网格点数 (步长 0.5m，共 81 个采样点) */
    public static final int GRID_Y_STEPS = 81;

    /** X轴网格步长 (m) */
    public static final double GRID_DX = RAFT_LENGTH / (GRID_X_STEPS - 1);

    /** Y轴网格步长 (m) */
    public static final double GRID_DY = RAFT_WIDTH / (GRID_Y_STEPS - 1);

    // ==================== 算法参数 ====================

    /** 小U融合滑动平均窗口大小 */
    public static final int FUSION_MA_WINDOW = 15;

    /** 数据补齐时外推参考点数 */
    public static final int PAD_REF_POINTS = 3;

    /** 尾部延伸X方向步长 (m) */
    public static final int GHOST_TAIL_STEP_X = 1;

    /** 尾部延伸/前端约束 Y方向步长 (m) */
    public static final int GHOST_Y_STEP = 2;

    // ==================== 排体目标长度查询 ====================

    /**
     * 根据排体号获取大U目标长度
     */
    public static int getLargeULength(String rowBody) {
        return GROUP_195_90.contains(rowBody) ? LARGE_LEN_195 : LARGE_LEN_180;
    }

    /**
     * 根据排体号获取小U目标长度
     */
    public static int getSmallULength(String rowBody) {
        return GROUP_195_90.contains(rowBody) ? SMALL_LEN_90 : SMALL_LEN_70;
    }
}
