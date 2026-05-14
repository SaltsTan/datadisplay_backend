package org.jeecg.modules.basic.util;

import org.jeecg.modules.basic.config.DeformationConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 幽灵节点构建服务
 * 在光缆外侧盲区（Y=0~10, 排尾区域）构建约束节点，防止3D渲染引擎生成翘曲/乱飞的网格
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
public class GhostNodeService {

    private static final double RAFT_LENGTH = DeformationConstants.RAFT_LENGTH;
    private static final double RAFT_WIDTH = DeformationConstants.RAFT_WIDTH;
    private static final int GHOST_Y_STEP = DeformationConstants.GHOST_Y_STEP;

    /**
     * 幽灵节点（约束点）
     */
    public static class GhostNode {
        public final double x;
        public final double y;
        public final double z;
        public final boolean isGhost;

        public GhostNode(double x, double y, double z, boolean isGhost) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.isGhost = isGhost;
        }
    }

    /**
     * 构建全部四组幽灵节点
     *
     * @param zLargeLeft   大U左侧Z值
     * @param zLargeRight  大U右侧Z值
     * @param zLargeBottom 大U横底边Z值
     * @param nLarge       大U长度
     * @return 幽灵节点列表
     */
    public List<GhostNode> buildGhostNodes(double[] zLargeLeft, double[] zLargeRight,
                                            double[] zLargeBottom, int nLarge) {
        List<GhostNode> nodes = new ArrayList<>();

        nodes.addAll(buildRightEdge(zLargeRight, nLarge));
        nodes.addAll(buildLeftEdge(zLargeLeft, nLarge));
        nodes.addAll(buildTailExtension(zLargeBottom, nLarge));
        nodes.addAll(buildFrontConstraint(zLargeLeft, zLargeRight));

        return nodes;
    }

    private List<GhostNode> buildRightEdge(double[] zLargeRight, int nLarge) {
        List<GhostNode> nodes = new ArrayList<>();
        for (int i = 0; i < nLarge; i++) {
            nodes.add(new GhostNode(i, RAFT_WIDTH, zLargeRight[i], true));
        }
        return nodes;
    }

    private List<GhostNode> buildLeftEdge(double[] zLargeLeft, int nLarge) {
        List<GhostNode> nodes = new ArrayList<>();
        for (int i = 0; i < nLarge; i++) {
            nodes.add(new GhostNode(i, 0.0, zLargeLeft[i], true));
        }
        return nodes;
    }

    private List<GhostNode> buildTailExtension(double[] zLargeBottom, int nLarge) {
        List<GhostNode> nodes = new ArrayList<>();
        double zRef = zLargeBottom != null && zLargeBottom.length > 0
                ? zLargeBottom[zLargeBottom.length - 1] : 0.0;

        for (int x = nLarge; x <= RAFT_LENGTH; x++) {
            for (int y = 0; y <= RAFT_WIDTH; y += GHOST_Y_STEP) {
                nodes.add(new GhostNode(x, (double) y, zRef, true));
            }
        }
        return nodes;
    }

    private List<GhostNode> buildFrontConstraint(double[] zLargeLeft, double[] zLargeRight) {
        List<GhostNode> nodes = new ArrayList<>();
        double zL0 = zLargeLeft.length > 0 ? zLargeLeft[0] : 0.0;
        double zR0 = zLargeRight.length > 0 ? zLargeRight[0] : 0.0;

        for (int y = 0; y <= RAFT_WIDTH; y += GHOST_Y_STEP) {
            double ratio = y / RAFT_WIDTH;
            double z = zL0 * (1.0 - ratio) + zR0 * ratio;
            nodes.add(new GhostNode(0.0, (double) y, z, true));
        }
        return nodes;
    }
}
