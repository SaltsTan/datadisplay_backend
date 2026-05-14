package org.jeecg.modules.basic.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 曲面网格数据传输对象
 * 包含插值后的Z值网格 + 六条光缆轨迹 + 特殊标记
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
@Data
public class SurfaceMeshDTO {

    /** 排体号 (如 "19"~"25") */
    private String rowBody;

    /** X方向网格点数 (201) */
    private int gridWidth;

    /** Y方向网格点数 (81) */
    private int gridHeight;

    /** X轴范围 */
    private double xMin;
    private double xMax;

    /** Y轴范围 */
    private double yMin;
    private double yMax;

    /**
     * Z值二维矩阵 [gridWidth][gridHeight]
     * zGrid[i][j] 对应点 (xMin + i * dx, yMin + j * dy)
     */
    @JsonProperty("zGrid")  // 确保序列化为 zGrid 而不是 zgrid
    private double[][] zGrid;

    // ========== 六条光缆轨迹（用于前端3D高亮渲染） ==========

    /** 大U左侧轨迹: [[x, y, z], ...] */
    private List<double[]> largeULeft;

    /** 大U右侧轨迹: [[x, y, z], ...] */
    private List<double[]> largeURight;

    /** 大U横底边轨迹: [[x, y, z], ...] */
    private List<double[]> largeUBottom;

    /** 小U左侧轨迹: [[x, y, z], ...] */
    private List<double[]> smallULeft;

    /** 小U右侧轨迹: [[x, y, z], ...] */
    private List<double[]> smallURight;

    /** 小U横底边轨迹: [[x, y, z], ...] */
    private List<double[]> smallUBottom;

    // ========== 特殊标记 ==========

    /** 是否存在断裂光缆 (#19排体) */
    private boolean hasBrokenCable;

    /** 幽灵节点在大U左侧轨迹中的索引（这些点仅用于插值约束，前端不渲染） */
    private List<Integer> ghostCableIndices;
}
