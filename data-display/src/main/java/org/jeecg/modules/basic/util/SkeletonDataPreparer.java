package org.jeecg.modules.basic.util;

import org.jeecg.modules.basic.config.CableLengthConfig;
import org.jeecg.modules.basic.config.DeformationConstants;
import org.jeecg.modules.basic.dto.PointDTO;
import org.jeecg.modules.basic.dto.SkeletonCurveDTO;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 骨架曲线数据准备工具
 * 从 processPipeline 已完成反演的 PointDTO 列表中，按 originalY 物理坐标
 * 提取六条光缆曲线的 deformation 值序列（截取/补齐/断裂处理）
 * 
 * 重要：每条边的目标长度根据 CableLengthConfig 配置，左右边可能不同
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
public class SkeletonDataPreparer {

    public SkeletonDataPreparer() {}

    /**
     * 从反演结果中提取某排体的六条骨架曲线
     *
     * @param rowBody   排体号
     * @param allPoints 该排体的所有 PointDTO（已由 processPipeline 完成反演计算）
     * @return 六条骨架曲线列表（LARGE_LEFT, LARGE_RIGHT, LARGE_BOTTOM, SMALL_LEFT, SMALL_RIGHT, SMALL_BOTTOM）
     */
    public List<SkeletonCurveDTO> extractSkeletonCurves(String rowBody, List<PointDTO> allPoints) {
        if (allPoints == null || allPoints.isEmpty()) {
            return new ArrayList<>();
        }

        Map<SkeletonCurveDTO.CurveType, SkeletonCurveDTO> curves = new LinkedHashMap<>();

        ChannelGroupHandler handler = new ChannelGroupHandler(allPoints);

        // 大U左侧：根据配置获取目标长度
        int largeLeftTarget = CableLengthConfig.getTargetLength(rowBody, CableLengthConfig.CableEdge.LARGE_U_LEFT);
        double[] zLL = handler.getDeformationByFixedY(DeformationConstants.BIG_U_LEFT_Y);
        curves.put(SkeletonCurveDTO.CurveType.LARGE_LEFT,
                new SkeletonCurveDTO(rowBody, SkeletonCurveDTO.CurveType.LARGE_LEFT,
                        padOrTrim(zLL, largeLeftTarget), largeLeftTarget, DeformationConstants.BIG_U_LEFT_Y));

        // 大U右侧：根据配置获取目标长度
        int largeRightTarget = CableLengthConfig.getTargetLength(rowBody, CableLengthConfig.CableEdge.LARGE_U_RIGHT);
        double[] zLR = handler.getDeformationByFixedY(DeformationConstants.BIG_U_RIGHT_Y);
        curves.put(SkeletonCurveDTO.CurveType.LARGE_RIGHT,
                new SkeletonCurveDTO(rowBody, SkeletonCurveDTO.CurveType.LARGE_RIGHT,
                        padOrTrim(zLR, largeRightTarget), largeRightTarget, DeformationConstants.BIG_U_RIGHT_Y));

        // 小U左侧：根据配置获取目标长度
        int smallLeftTarget = CableLengthConfig.getTargetLength(rowBody, CableLengthConfig.CableEdge.SMALL_U_LEFT);
        double[] zSL = handler.getDeformationByFixedY(DeformationConstants.SMALL_U_LEFT_Y);
        curves.put(SkeletonCurveDTO.CurveType.SMALL_LEFT,
                new SkeletonCurveDTO(rowBody, SkeletonCurveDTO.CurveType.SMALL_LEFT,
                        padOrTrim(zSL, smallLeftTarget), smallLeftTarget, DeformationConstants.SMALL_U_LEFT_Y));

        // 小U右侧：根据配置获取目标长度
        int smallRightTarget = CableLengthConfig.getTargetLength(rowBody, CableLengthConfig.CableEdge.SMALL_U_RIGHT);
        double[] zSR = handler.getDeformationByFixedY(DeformationConstants.SMALL_U_RIGHT_Y);
        curves.put(SkeletonCurveDTO.CurveType.SMALL_RIGHT,
                new SkeletonCurveDTO(rowBody, SkeletonCurveDTO.CurveType.SMALL_RIGHT,
                        padOrTrim(zSR, smallRightTarget), smallRightTarget, DeformationConstants.SMALL_U_RIGHT_Y));

        // 大U横边：使用大U左侧的目标长度作为参考
        double[] zLB = handler.getDeformationVaryingY(DeformationConstants.BIG_U_LEFT_Y,
                DeformationConstants.BIG_U_RIGHT_Y, largeLeftTarget);
        curves.put(SkeletonCurveDTO.CurveType.LARGE_BOTTOM,
                new SkeletonCurveDTO(rowBody, SkeletonCurveDTO.CurveType.LARGE_BOTTOM,
                        zLB, zLB.length, null));

        // 小U横边：使用小U左侧的目标长度作为参考
        double[] zSB = handler.getDeformationVaryingY(DeformationConstants.SMALL_U_LEFT_Y,
                DeformationConstants.SMALL_U_RIGHT_Y, smallLeftTarget);
        curves.put(SkeletonCurveDTO.CurveType.SMALL_BOTTOM,
                new SkeletonCurveDTO(rowBody, SkeletonCurveDTO.CurveType.SMALL_BOTTOM,
                        zSB, zSB.length, null));

        // #19号排体特殊处理：大U右侧断裂
        if ("19".equals(rowBody)) {
            applyP19SpecialHandling(curves);
        }

        return new ArrayList<>(curves.values());
    }

    /**
     * 按通道分组、按x坐标排序后提取 deformation 值序列
     */
    private static class ChannelGroupHandler {

        private final Map<String, List<PointDTO>> channelGroups;
        private final LinkedHashMap<String, List<PointDTO>> sortedGroups;

        ChannelGroupHandler(List<PointDTO> allPoints) {
            this.channelGroups = allPoints.stream()
                    .collect(Collectors.groupingBy(p -> {
                        String ch = p.getChannel();
                        int sIdx = ch.indexOf("_S");
                        return sIdx > 0 ? ch.substring(0, sIdx) : ch;
                    }, LinkedHashMap::new, Collectors.toList()));

            this.sortedGroups = new LinkedHashMap<>();
            for (Map.Entry<String, List<PointDTO>> entry : channelGroups.entrySet()) {
                List<PointDTO> pts = new ArrayList<>(entry.getValue());
                pts.sort(Comparator.comparingDouble(PointDTO::getX));
                sortedGroups.put(entry.getKey(), pts);
            }
        }

        /**
         * 从所有通道中，找到 originalY 值全部等于 fixedY 的通道，合并后返回 deformation[]
         * 排序按通道的 X 坐标排（跨多个通道的同一物理边）
         */
        double[] getDeformationByFixedY(double fixedY) {
            List<PointDTO> matched = new ArrayList<>();

            for (Map.Entry<String, List<PointDTO>> entry : sortedGroups.entrySet()) {
                List<PointDTO> pts = entry.getValue();
                if (pts.isEmpty()) continue;

                Set<Double> ySet = pts.stream().map(PointDTO::getOriginalY).collect(Collectors.toSet());
                if (ySet.size() == 1 && Math.abs(ySet.iterator().next() - fixedY) < 0.5) {
                    matched.addAll(pts);
                }
            }

            if (matched.isEmpty()) return new double[0];

            matched.sort(Comparator.comparingDouble(PointDTO::getX));
            return matched.stream().mapToDouble(PointDTO::getDeformation).toArray();
        }

        /**
         * 从所有通道中找 Y 值变化的通道（横边），返回 deformation[]
         * 按X降序排列（模拟从右→左的横边方向）
         */
        double[] getDeformationVaryingY(double leftY, double rightY, int expectedLen) {
            List<PointDTO> matched = new ArrayList<>();

            for (Map.Entry<String, List<PointDTO>> entry : sortedGroups.entrySet()) {
                List<PointDTO> pts = entry.getValue();
                if (pts.isEmpty()) continue;

                Set<Double> ySet = pts.stream().map(PointDTO::getOriginalY).collect(Collectors.toSet());
                if (ySet.size() > 1) {
                    matched.addAll(pts);
                }
            }

            if (matched.isEmpty()) return new double[0];

            matched.sort(Comparator.comparingDouble((PointDTO p) -> p.getX()).reversed());
            return matched.stream().mapToDouble(PointDTO::getDeformation).toArray();
        }
    }

    private double[] padOrTrim(double[] data, int targetLength) {
        if (data == null || data.length == 0) {
            double[] result = new double[targetLength];
            Arrays.fill(result, 0.0);
            return result;
        }

        if (data.length >= targetLength) {
            double[] result = new double[targetLength];
            System.arraycopy(data, data.length - targetLength, result, 0, targetLength);
            return result;
        }

        int need = targetLength - data.length;
        double[] result = new double[targetLength];

        int refCount = Math.min(DeformationConstants.PAD_REF_POINTS, data.length);
        double[] refValues = Arrays.copyOfRange(data, 0, refCount);

        double slope = 0;
        if (refCount >= 2) {
            for (int i = 0; i < refCount - 1; i++) {
                slope += refValues[i + 1] - refValues[i];
            }
            slope /= (refCount - 1);
        }

        for (int i = 0; i < need; i++) {
            result[i] = refValues[0] + slope * (i - need);
        }
        System.arraycopy(data, 0, result, need, data.length);

        return result;
    }

    /**
     * #19号排体特殊处理：大U右侧断裂
     * 
     * 处理逻辑（按照需求文档）：
     * 1. 左侧完好，有195个传感器点
     * 2. 右侧断裂，仅有111个传感器点
     * 3. 用左侧前84个点（195-111=84）补充右侧缺失的数据
     * 4. 右侧后111个点统一补偿第84个传感器的反演值
     * 
     * 公式：Z_R(x) = Z_R_Tail(x) + Z_L(84)，其中 x ∈ [84, 195]
     * 
     * 注意：前84个点仅用于插值，不显示（标记为灰色或不显示点）
     */
    private void applyP19SpecialHandling(Map<SkeletonCurveDTO.CurveType, SkeletonCurveDTO> curves) {
        SkeletonCurveDTO largeLeft = curves.get(SkeletonCurveDTO.CurveType.LARGE_LEFT);
        SkeletonCurveDTO largeRight = curves.get(SkeletonCurveDTO.CurveType.LARGE_RIGHT);

        if (largeLeft == null || largeRight == null) return;

        double[] zLeft = largeLeft.getZValues();
        double[] zRightBroken = largeRight.getZValues();

        // 验证数据长度
        if (zLeft.length < DeformationConstants.P25_LEFT_COUNT) {
            System.err.println("警告：#19左侧数据不足195个点，实际: " + zLeft.length);
            return;
        }
        if (zRightBroken.length < DeformationConstants.P25_RIGHT_COUNT) {
            System.err.println("警告：#19右侧数据不足111个点，实际: " + zRightBroken.length);
            return;
        }

        int supplementCount = DeformationConstants.P25_LEFT_COUNT - DeformationConstants.P25_RIGHT_COUNT; // 84

        // 构建修复后的右侧数据（195个点）
        double[] zRightFixed = new double[DeformationConstants.P25_LEFT_COUNT];

        // 前84个点：直接复制左侧前84个点
        System.arraycopy(zLeft, 0, zRightFixed, 0, supplementCount);

        // 后111个点：右侧断裂数据 + 第84个点的补偿值
        double compensation = zLeft[supplementCount - 1]; // 左侧第84个点的值（索引83）
        for (int i = 0; i < DeformationConstants.P25_RIGHT_COUNT; i++) {
            zRightFixed[supplementCount + i] = zRightBroken[i] + compensation;
        }

        // 更新右侧数据
        largeRight.setZValues(zRightFixed);
        largeRight.setRequiredLength(DeformationConstants.P25_LEFT_COUNT);
        largeRight.setGhostNode(true); // 标记为包含幽灵节点（前84个点）
        
        System.out.println("#19号排体特殊处理完成：左侧" + zLeft.length + "点，右侧修复为" + zRightFixed.length + "点（前" + supplementCount + "个为补偿点）");
    }
}
