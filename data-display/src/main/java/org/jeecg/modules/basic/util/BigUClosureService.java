package org.jeecg.modules.basic.util;

/**
 * 大U光缆横边闭合服务
 * 确保大U左右两侧与横边在3D空间中形成闭合图形
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
public class BigUClosureService {

    /**
     * 对横边Z值进行闭合修正
     *
     * @param zLeft   大U左侧边Z值 (Z_LL)
     * @param zRight  大U右侧边Z值 (Z_LR)
     * @param zBottom 大U横底边Z值 (Z_LB)
     * @return 闭合修正后的横边Z值
     */
    public double[] closeBottom(double[] zLeft, double[] zRight, double[] zBottom) {
        if (zBottom == null || zBottom.length == 0) return new double[0];

        int nBottom = zBottom.length;
        double[] result = new double[nBottom];
        System.arraycopy(zBottom, 0, result, 0, nBottom);

        if (zLeft == null || zLeft.length == 0 || zRight == null || zRight.length == 0) {
            return result;
        }

        double targetDiff = zLeft[zLeft.length - 1] - zRight[zRight.length - 1];
        double currentDiff = zBottom[nBottom - 1] - zBottom[0];
        double error = targetDiff - currentDiff;

        for (int i = 0; i < nBottom; i++) {
            double ratio = nBottom > 1 ? (double) i / (nBottom - 1) : 0;
            result[i] += error * ratio;
        }

        return result;
    }
}
