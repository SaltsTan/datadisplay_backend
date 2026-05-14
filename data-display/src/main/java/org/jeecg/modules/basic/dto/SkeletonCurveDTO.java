package org.jeecg.modules.basic.dto;

import lombok.Data;

/**
 * 骨架曲线数据对象
 * 表示大U或小U的一条边（左侧/右侧/横底边）
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
@Data
public class SkeletonCurveDTO {

    /** 曲线类型 */
    public enum CurveType {
        /** 大U左侧边 */
        LARGE_LEFT,
        /** 大U右侧边 */
        LARGE_RIGHT,
        /** 大U横底边 */
        LARGE_BOTTOM,
        /** 小U左侧边 */
        SMALL_LEFT,
        /** 小U右侧边 */
        SMALL_RIGHT,
        /** 小U横底边 */
        SMALL_BOTTOM
    }

    /** 排体号 (如 "19"~"25") */
    private String rowBody;

    /** 曲线类型 */
    private CurveType curveType;

    /** 形变Z值序列 */
    private double[] zValues;

    /** 目标长度 (根据分组规则: 大U 195/180, 小U 90/70) */
    private int requiredLength;

    /** 固定Y坐标 (横边为 null) */
    private Double fixedY;

    /** 是否为约束点（幽灵节点/断裂补偿段），用于标记不参与可视化显示 */
    private boolean ghostNode;

    public SkeletonCurveDTO() {}

    public SkeletonCurveDTO(String rowBody, CurveType curveType, double[] zValues,
                            int requiredLength, Double fixedY) {
        this.rowBody = rowBody;
        this.curveType = curveType;
        this.zValues = zValues;
        this.requiredLength = requiredLength;
        this.fixedY = fixedY;
        this.ghostNode = false;
    }
}
