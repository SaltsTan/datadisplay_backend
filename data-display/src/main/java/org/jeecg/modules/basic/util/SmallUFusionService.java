package org.jeecg.modules.basic.util;

import org.jeecg.modules.basic.config.DeformationConstants;

/**
 * 小U光缆主从基准融合服务
 * 以小U位置的大U"宏观地形"为基准线，叠加小U的高频细节
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
public class SmallUFusionService {

    private final BigUClosureService bigUClosureService;

    public SmallUFusionService(BigUClosureService bigUClosureService) {
        this.bigUClosureService = bigUClosureService;
    }

    /**
     * 将小U数据融合到大U宏观基准上
     *
     * @param zLargeLeft  大U左侧边Z值 (Z_LL)
     * @param zLargeRight 大U右侧边Z值 (Z_LR)
     * @param zSmallLeft  小U左侧边原始Z值 (Z_SL)
     * @param zSmallRight 小U右侧边原始Z值 (Z_SR)
     * @param zSmallBottom 小U横底边原始Z值 (Z_SB)
     * @return FusionResult 包含融合后的小U左右边和闭合并修正后的横边
     */
    public FusionResult fuse(double[] zLargeLeft, double[] zLargeRight,
                              double[] zSmallLeft, double[] zSmallRight,
                              double[] zSmallBottom) {
        int nSmall = zSmallLeft.length;

        double[] zLargeLeftSub = takeFirstN(zLargeLeft, nSmall);
        double[] zLargeRightSub = takeFirstN(zLargeRight, nSmall);

        double[] baseSL = new double[nSmall];
        double[] baseSR = new double[nSmall];

        double weightLeft = 0.75;
        double weightRight = 0.25;

        for (int i = 0; i < nSmall; i++) {
            baseSL[i] = weightLeft * zLargeLeftSub[i] + weightRight * zLargeRightSub[i];
            baseSR[i] = weightRight * zLargeLeftSub[i] + weightLeft * zLargeRightSub[i];
        }

        double[] detailSL = extractDetail(zSmallLeft);
        double[] detailSR = extractDetail(zSmallRight);

        double[] zSmallLeftFused = new double[nSmall];
        double[] zSmallRightFused = new double[nSmall];

        for (int i = 0; i < nSmall; i++) {
            zSmallLeftFused[i] = baseSL[i] + detailSL[i];
            zSmallRightFused[i] = baseSR[i] + detailSR[i];
        }

        double[] zSmallBottomClosed = bigUClosureService.closeBottom(
                zSmallLeftFused, zSmallRightFused, zSmallBottom);

        return new FusionResult(zSmallLeftFused, zSmallRightFused, zSmallBottomClosed);
    }

    /**
     * 提取高频细节：Z_raw - movingAverage(Z_raw, window)
     */
    private double[] extractDetail(double[] data) {
        if (data == null || data.length == 0) return new double[0];

        double[] trend = MathUtils.movingAverage(data, DeformationConstants.FUSION_MA_WINDOW);
        double[] detail = new double[data.length];

        for (int i = 0; i < data.length; i++) {
            detail[i] = data[i] - trend[i];
        }
        return detail;
    }

    private double[] takeFirstN(double[] data, int n) {
        if (data == null) return new double[n];
        int len = Math.min(data.length, n);
        double[] result = new double[n];
        System.arraycopy(data, 0, result, 0, len);
        return result;
    }

    /**
     * 小U融合结果
     */
    public static class FusionResult {
        public final double[] zSmallLeftFused;
        public final double[] zSmallRightFused;
        public final double[] zSmallBottomClosed;

        public FusionResult(double[] zSmallLeftFused, double[] zSmallRightFused,
                            double[] zSmallBottomClosed) {
            this.zSmallLeftFused = zSmallLeftFused;
            this.zSmallRightFused = zSmallRightFused;
            this.zSmallBottomClosed = zSmallBottomClosed;
        }
    }
}
