package org.jeecg.modules.basic.util;

import org.jeecg.modules.basic.dto.PointDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 边分割器：将过滤后的传感器数据拆分为6条独立的边
 * <p>
 * 处理流程：
 * 1. 按相邻点欧氏距离>5m断开为多个形状段
 * 2. 识别大U（最长且长度>150的形状段）
 * 3. 通过坐标方向变化检测拐点，将大U拆分为左侧、横边、右侧
 * 4. 将剩余形状段按Y坐标分类为小U的左侧、右侧、横边
 * 5. 对每条边独立维护从0开始连续编号的pointIndex
 */
public class EdgeSplitter {

    /**
     * 边分割结果，包含6条独立的边数据
     */
    public static class EdgeResult {
        public List<PointDTO> largeULeft;    // 大U左侧
        public List<PointDTO> largeURight;   // 大U右侧
        public List<PointDTO> largeUBottom;  // 大U横边
        public List<PointDTO> smallULeft;    // 小U左侧
        public List<PointDTO> smallURight;   // 小U右侧
        public List<PointDTO> smallUBottom;  // 小U横边

        public EdgeResult() {
            this.largeULeft = new ArrayList<>();
            this.largeURight = new ArrayList<>();
            this.largeUBottom = new ArrayList<>();
            this.smallULeft = new ArrayList<>();
            this.smallURight = new ArrayList<>();
            this.smallUBottom = new ArrayList<>();
        }
    }

    /**
     * 将过滤后的点数据分割为6条边
     *
     * @param filteredPoints 经过岸边过滤后的点数据列表
     * @param rowBody        排体编号（如 "19"~"25"）
     * @return EdgeResult 包含6条边的分割结果
     */
    public static EdgeResult split(List<PointDTO> filteredPoints, String rowBody) {
        EdgeResult result = new EdgeResult();

        if (filteredPoints == null || filteredPoints.isEmpty()) {
            return result;
        }

        // Step 1: 按相邻点欧氏距离>5m断开为多个形状段
        List<List<PointDTO>> shapes = splitIntoShapes(filteredPoints);

        if (shapes.isEmpty()) {
            return result;
        }

        // Step 2: 识别大U（最长且长度>150的形状段）
        List<PointDTO> largeU = identifyLargeU(shapes, rowBody);

        // Step 3: 通过坐标方向变化检测拐点，将大U拆分为左侧、横边、右侧
        if (largeU != null && !largeU.isEmpty()) {
            int[] corners = findCornerIndices(largeU);
            int c1 = corners[0];
            int c2 = corners[1];

            if (c1 > 0 && c2 > c1) {
                // 检测到两个拐点，拆分为三部分
                result.largeULeft = reindex(largeU.subList(0, c1 + 1));
                result.largeUBottom = reindex(largeU.subList(c1 + 1, c2 + 1));
                result.largeURight = reindex(largeU.subList(c2 + 1, largeU.size()));

                // 校正左右颠倒：大U形状的行走方向可能从Y≈29侧开始，导致拆分后的
                // "左"边实际Y≈29、"右"边实际Y≈10。按Y坐标分布判定并交换。
                if (!result.largeULeft.isEmpty() && !result.largeURight.isEmpty()) {
                    int leftAtY10 = countNearY(result.largeULeft, 10.0);
                    int leftAtY29 = countNearY(result.largeULeft, 29.0);
                    int rightAtY10 = countNearY(result.largeURight, 10.0);
                    int rightAtY29 = countNearY(result.largeURight, 29.0);
                    if (leftAtY29 > leftAtY10 && rightAtY10 > rightAtY29) {
                        List<PointDTO> tmp = result.largeULeft;
                        result.largeULeft = result.largeURight;
                        result.largeURight = tmp;
                    }
                }
            } else {
                // 未检测到两个拐点，大U作为单条边不拆分（放入largeULeft）
                result.largeULeft = reindex(largeU);
            }
        }

        // Step 4: 收集非大U的形状段，按Y坐标分类为小U
        List<List<PointDTO>> remainingShapes = new ArrayList<>();
        for (List<PointDTO> shape : shapes) {
            if (shape != largeU) {
                // 对25号排体，合并后的大U可能由多个原始shape组成，需要排除
                if (!isPartOfLargeU(shape, largeU)) {
                    remainingShapes.add(shape);
                }
            }
        }
        classifySmallUShapes(remainingShapes, result);

        return result;
    }

    /**
     * 按相邻点欧氏距离>5m断开为多个形状段
     */
    static List<List<PointDTO>> splitIntoShapes(List<PointDTO> points) {
        List<List<PointDTO>> shapes = new ArrayList<>();
        if (points == null || points.isEmpty()) return shapes;

        List<PointDTO> current = new ArrayList<>();
        current.add(points.get(0));

        for (int i = 1; i < points.size(); i++) {
            PointDTO p1 = points.get(i - 1);
            PointDTO p2 = points.get(i);
            double dist = Math.hypot(p2.getOriginalX() - p1.getOriginalX(),
                    p2.getOriginalY() - p1.getOriginalY());
            if (dist > 5.0) {
                shapes.add(current);
                current = new ArrayList<>();
            }
            current.add(p2);
        }
        shapes.add(current);
        return shapes;
    }

    /**
     * 识别大U：找到长度>150的最长形状段作为大U
     * 对25号排体，合并断开的子段（仅合并Y坐标为大U的段，即Y≈10或Y≈29）
     */
    static List<PointDTO> identifyLargeU(List<List<PointDTO>> shapes, String rowBody) {
        if ("25".equals(rowBody)) {
            // 25号排体特殊处理：合并断开的大U子段
            // 只合并Y坐标属于大U范围的段（Y≈10 或 Y≈29，或Y在10~29之间变化的横边段）
            List<List<PointDTO>> candidates = new ArrayList<>();
            int totalLength = 0;
            for (List<PointDTO> shape : shapes) {
                if (shape.isEmpty()) continue;
                // 判断该段是否属于大U：首点Y≈10或Y≈29
                double firstY = shape.get(0).getOriginalY();
                boolean isBigUY = Math.abs(firstY - 10.0) < 1.0 || Math.abs(firstY - 29.0) < 1.0;
                // 或者是横边（Y在10~29之间变化）
                if (!isBigUY) {
                    double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
                    for (PointDTO p : shape) {
                        minY = Math.min(minY, p.getOriginalY());
                        maxY = Math.max(maxY, p.getOriginalY());
                    }
                    isBigUY = (minY <= 11.0 && maxY >= 28.0); // 跨越大U左右两侧
                }
                if (isBigUY && shape.size() > 30) {
                    candidates.add(shape);
                    totalLength += shape.size();
                }
            }

            // 如果合并后总长度>150，则合并为大U
            if (totalLength > 150 && candidates.size() > 1) {
                List<PointDTO> merged = new ArrayList<>();
                for (List<PointDTO> candidate : candidates) {
                    merged.addAll(candidate);
                }
                return merged;
            }
        }

        // 通用逻辑：找到长度>150的最长形状段
        int maxLength = 0;
        List<PointDTO> largeU = null;
        for (List<PointDTO> shape : shapes) {
            if (shape.size() > maxLength && shape.size() > 150) {
                maxLength = shape.size();
                largeU = shape;
            }
        }
        return largeU;
    }

    /**
     * 通过坐标方向变化检测两个拐点
     * <p>
     * 使用滑动窗口检测方向变化：
     * 第一个拐点：前方窗口以X方向为主，后方窗口以Y方向为主
     * 第二个拐点：前方窗口以Y方向为主，后方窗口以X方向为主
     * <p>
     * 窗口大小为3个点，通过累计方向判断，兼容个别点缺失的情况。
     *
     * @return int[2]，分别为第一个和第二个拐点的索引，未检测到时为-1
     */
    static int[] findCornerIndices(List<PointDTO> points) {
        if (points == null || points.size() < 10) return new int[]{-1, -1};

        int c1 = -1, c2 = -1;
        int windowSize = 3;

        // 查找第一个拐点：前方X为主 → 后方Y为主
        for (int i = windowSize; i < points.size() - windowSize; i++) {
            // 前方窗口：检查 i-windowSize ~ i 这几步是否以X方向为主
            double prevDxSum = 0, prevDySum = 0;
            for (int k = i - windowSize; k < i; k++) {
                prevDxSum += Math.abs(points.get(k + 1).getOriginalX() - points.get(k).getOriginalX());
                prevDySum += Math.abs(points.get(k + 1).getOriginalY() - points.get(k).getOriginalY());
            }

            // 后方窗口：检查 i ~ i+windowSize 这几步是否以Y方向为主
            double nextDxSum = 0, nextDySum = 0;
            for (int k = i; k < i + windowSize; k++) {
                nextDxSum += Math.abs(points.get(k + 1).getOriginalX() - points.get(k).getOriginalX());
                nextDySum += Math.abs(points.get(k + 1).getOriginalY() - points.get(k).getOriginalY());
            }

            // 前方X为主（X累计 > Y累计 * 2）且后方Y为主（Y累计 > X累计 * 2）
            if (prevDxSum > prevDySum * 2 && nextDySum > nextDxSum * 2) {
                c1 = i;
                break;
            }
        }

        // 查找第二个拐点：前方Y为主 → 后方X为主
        int searchStart = c1 != -1 ? c1 + windowSize : windowSize;
        for (int i = searchStart; i < points.size() - windowSize; i++) {
            double prevDxSum = 0, prevDySum = 0;
            for (int k = i - windowSize; k < i; k++) {
                prevDxSum += Math.abs(points.get(k + 1).getOriginalX() - points.get(k).getOriginalX());
                prevDySum += Math.abs(points.get(k + 1).getOriginalY() - points.get(k).getOriginalY());
            }

            double nextDxSum = 0, nextDySum = 0;
            for (int k = i; k < i + windowSize; k++) {
                nextDxSum += Math.abs(points.get(k + 1).getOriginalX() - points.get(k).getOriginalX());
                nextDySum += Math.abs(points.get(k + 1).getOriginalY() - points.get(k).getOriginalY());
            }

            // 前方Y为主 且 后方X为主
            if (prevDySum > prevDxSum * 2 && nextDxSum > nextDySum * 2) {
                c2 = i;
                break;
            }
        }

        return new int[]{c1, c2};
    }

    /**
     * 将剩余形状段分类为小U的左侧、横边、右侧
     * <p>
     * 策略：
     * 1. 对每个形状段，先检查是否是"混合段"（Y范围跨越14~23，即包含左侧+横边+右侧）
     * 2. 混合段：用 findCornerIndices 检测拐点，分割为右侧/横边/左侧（与大U方向一致）
     * 3. 纯Y≈14段：归为左侧；纯Y≈23段：归为右侧；Y变化段：归为横边
     */
    static void classifySmallUShapes(List<List<PointDTO>> remainingShapes, EdgeResult result) {
        for (List<PointDTO> shape : remainingShapes) {
            if (shape.isEmpty()) continue;

            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            int countY14 = 0, countY23 = 0;

            for (PointDTO p : shape) {
                double y = p.getOriginalY();
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (Math.abs(y - 14.0) <= 1.0) countY14++;
                if (Math.abs(y - 23.0) <= 1.0) countY23++;
            }

            boolean isMixed = (minY <= 15.0 && maxY >= 22.0); // 跨越小U左右两侧

            if (isMixed && shape.size() >= 10) {
                // 混合段：用拐点检测分割为右侧/横边/左侧
                int[] corners = findCornerIndices(shape);
                int c1 = corners[0];
                int c2 = corners[1];

                if (c1 > 0 && c2 > c1) {
                    // 检测到两个拐点：[0,c1]=右侧, [c1+1,c2]=横边, [c2+1,end]=左侧
                    result.smallURight.addAll(shape.subList(0, c1 + 1));
                    result.smallUBottom.addAll(shape.subList(c1 + 1, c2 + 1));
                    result.smallULeft.addAll(shape.subList(c2 + 1, shape.size()));
                } else if (c1 > 0) {
                    // 只检测到一个拐点：[0,c1]=右侧, [c1+1,end]=横边+左侧（按Y分）
                    result.smallURight.addAll(shape.subList(0, c1 + 1));
                    splitByY(shape.subList(c1 + 1, shape.size()), result);
                } else {
                    // 未检测到拐点，按Y坐标直接分类
                    splitByY(shape, result);
                }
            } else if ((double) countY14 / shape.size() >= 0.7) {
                // 提取过渡点（Y不在14±0.5范围）到横边，使小U底部不被左右边吞并
                for (PointDTO p : shape) {
                    if (Math.abs(p.getOriginalY() - 14.0) <= 0.5) {
                        result.smallULeft.add(p);
                    } else {
                        result.smallUBottom.add(p);
                    }
                }
            } else if ((double) countY23 / shape.size() >= 0.7) {
                // 提取过渡点到横边
                for (PointDTO p : shape) {
                    if (Math.abs(p.getOriginalY() - 23.0) <= 0.5) {
                        result.smallURight.add(p);
                    } else {
                        result.smallUBottom.add(p);
                    }
                }
            } else if (maxY - minY > 1.0 && minY >= 13.0 && maxY <= 24.0) {
                result.smallUBottom.addAll(shape);
            }
        }

        // 对小U各边重新编号pointIndex
        result.smallULeft = reindex(result.smallULeft);
        result.smallURight = reindex(result.smallURight);
        result.smallUBottom = reindex(result.smallUBottom);
    }

    /**
     * 按Y坐标将点列表直接分类到小U各边（无拐点时的降级处理）
     */
    private static void splitByY(List<PointDTO> points, EdgeResult result) {
        for (PointDTO p : points) {
            double y = p.getOriginalY();
            if (Math.abs(y - 14.0) <= 1.5) {
                result.smallULeft.add(p);
            } else if (Math.abs(y - 23.0) <= 1.5) {
                result.smallURight.add(p);
            } else if (y > 14.0 && y < 23.0) {
                result.smallUBottom.add(p);
            }
        }
    }

    /**
     * 判断一个shape是否是大U合并的一部分（用于25号排体）
     */
    /** 统计点列表中 Y 坐标接近 targetY（容差 ±0.5）的点数 */
    private static int countNearY(List<PointDTO> points, double targetY) {
        int count = 0;
        for (PointDTO p : points) {
            if (Math.abs(p.getOriginalY() - targetY) <= 0.5) count++;
        }
        return count;
    }

    private static boolean isPartOfLargeU(List<PointDTO> shape, List<PointDTO> largeU) {
        if (largeU == null || shape == null || shape.isEmpty() || largeU.isEmpty()) {
            return false;
        }
        // 如果largeU包含该shape的第一个点，则认为是大U的一部分
        PointDTO first = shape.get(0);
        for (PointDTO p : largeU) {
            if (p == first) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对点列表重新编号pointIndex，从0开始连续编号
     */
    private static List<PointDTO> reindex(List<PointDTO> points) {
        List<PointDTO> result = new ArrayList<>(points);
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setPointIndex(i);
        }
        return result;
    }
}
