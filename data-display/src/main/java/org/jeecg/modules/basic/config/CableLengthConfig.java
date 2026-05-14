package org.jeecg.modules.basic.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 光缆长度配置
 * 根据需求文档中的反演参数表格，为每个排体的每条边配置目标长度
 * 
 * @author Senior Developer
 * @date 2026-05-13
 */
public final class CableLengthConfig {

    private CableLengthConfig() {}

    /**
     * 光缆边类型
     */
    public enum CableEdge {
        LARGE_U_LEFT,    // 大U左侧
        LARGE_U_RIGHT,   // 大U右侧
        SMALL_U_LEFT,    // 小U左侧
        SMALL_U_RIGHT    // 小U右侧
    }

    /**
     * 每个排体每条边的目标长度配置
     * 格式: Map<排体号, Map<边类型, 目标长度>>
     */
    private static final Map<String, Map<CableEdge, Integer>> CABLE_LENGTHS = new HashMap<>();

    static {
        // #19号排体
        Map<CableEdge, Integer> row19 = new HashMap<>();
        row19.put(CableEdge.LARGE_U_LEFT, 204);   // 实际204，取后195
        row19.put(CableEdge.LARGE_U_RIGHT, 220);  // 实际220，取后195
        row19.put(CableEdge.SMALL_U_LEFT, 90);
        row19.put(CableEdge.SMALL_U_RIGHT, 90);
        CABLE_LENGTHS.put("19", row19);

        // #20号排体
        Map<CableEdge, Integer> row20 = new HashMap<>();
        row20.put(CableEdge.LARGE_U_LEFT, 220);   // 实际220，取后195
        row20.put(CableEdge.LARGE_U_RIGHT, 220);  // 实际220，取后195
        row20.put(CableEdge.SMALL_U_LEFT, 90);
        row20.put(CableEdge.SMALL_U_RIGHT, 90);
        CABLE_LENGTHS.put("20", row20);

        // #21号排体
        Map<CableEdge, Integer> row21 = new HashMap<>();
        row21.put(CableEdge.LARGE_U_LEFT, 174);   // 实际174，需补齐到180
        row21.put(CableEdge.LARGE_U_RIGHT, 183);  // 实际183，取后180
        row21.put(CableEdge.SMALL_U_LEFT, 90);    // 实际90，取后70
        row21.put(CableEdge.SMALL_U_RIGHT, 90);   // 实际90，取后70
        CABLE_LENGTHS.put("21", row21);

        // #22号排体
        Map<CableEdge, Integer> row22 = new HashMap<>();
        row22.put(CableEdge.LARGE_U_LEFT, 165);   // 实际165，需补齐到180
        row22.put(CableEdge.LARGE_U_RIGHT, 200);  // 实际200，取后180
        row22.put(CableEdge.SMALL_U_LEFT, 70);
        row22.put(CableEdge.SMALL_U_RIGHT, 70);
        CABLE_LENGTHS.put("22", row22);

        // #23号排体
        Map<CableEdge, Integer> row23 = new HashMap<>();
        row23.put(CableEdge.LARGE_U_LEFT, 202);   // 实际202，取后180
        row23.put(CableEdge.LARGE_U_RIGHT, 188);  // 实际188，取后180
        row23.put(CableEdge.SMALL_U_LEFT, 70);
        row23.put(CableEdge.SMALL_U_RIGHT, 70);
        CABLE_LENGTHS.put("23", row23);

        // #24号排体
        Map<CableEdge, Integer> row24 = new HashMap<>();
        row24.put(CableEdge.LARGE_U_LEFT, 220);   // 实际220，取后195
        row24.put(CableEdge.LARGE_U_RIGHT, 220);  // 实际220，取后195
        row24.put(CableEdge.SMALL_U_LEFT, 90);
        row24.put(CableEdge.SMALL_U_RIGHT, 90);
        CABLE_LENGTHS.put("24", row24);

        // #25号排体
        Map<CableEdge, Integer> row25 = new HashMap<>();
        row25.put(CableEdge.LARGE_U_LEFT, 220);   // 实际220，取后195
        row25.put(CableEdge.LARGE_U_RIGHT, 111);  // 实际111（注意：表格显示111，不是断裂）
        row25.put(CableEdge.SMALL_U_LEFT, 90);
        row25.put(CableEdge.SMALL_U_RIGHT, 90);
        CABLE_LENGTHS.put("25", row25);
    }

    /**
     * 获取指定排体指定边的实际传感器个数
     * 
     * @param rowBody 排体号
     * @param edge 边类型
     * @return 实际传感器个数，如果未配置则返回0
     */
    public static int getActualLength(String rowBody, CableEdge edge) {
        Map<CableEdge, Integer> rowConfig = CABLE_LENGTHS.get(rowBody);
        if (rowConfig == null) {
            return 0;
        }
        return rowConfig.getOrDefault(edge, 0);
    }

    /**
     * 获取指定排体指定边的目标长度（截取/补齐后的长度）
     * 
     * @param rowBody 排体号
     * @param edge 边类型
     * @return 目标长度
     */
    public static int getTargetLength(String rowBody, CableEdge edge) {
        int actualLength = getActualLength(rowBody, edge);
        
        // 根据排体分组和边类型确定目标长度
        boolean isGroup195 = DeformationConstants.GROUP_195_90.contains(rowBody);
        
        switch (edge) {
            case LARGE_U_LEFT:
            case LARGE_U_RIGHT:
                // 大U目标长度
                int largeTarget = isGroup195 ? 195 : 180;
                
                // #25号右侧断裂特殊情况：实际111，目标195（会在后续补齐）
                if ("25".equals(rowBody) && edge == CableEdge.LARGE_U_RIGHT) {
                    return 195;
                }
                
                return largeTarget;
                
            case SMALL_U_LEFT:
            case SMALL_U_RIGHT:
                // 小U目标长度
                return isGroup195 ? 90 : 70;
                
            default:
                return actualLength;
        }
    }

    /**
     * 判断是否需要补齐（实际长度 < 目标长度）
     */
    public static boolean needsPadding(String rowBody, CableEdge edge) {
        return getActualLength(rowBody, edge) < getTargetLength(rowBody, edge);
    }

    /**
     * 判断是否需要截取（实际长度 > 目标长度）
     */
    public static boolean needsTrimming(String rowBody, CableEdge edge) {
        return getActualLength(rowBody, edge) > getTargetLength(rowBody, edge);
    }
}
