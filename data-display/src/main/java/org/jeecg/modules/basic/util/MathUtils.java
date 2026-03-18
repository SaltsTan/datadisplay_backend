package org.jeecg.modules.basic.util;

import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Arrays;

/**
 * 核心数学算法工具类：包含中值滤波、SG滤波、梯形积分等
 */
public class MathUtils {

    /**
     * 1. 中值滤波 (Median Filter) - 去除飞点野值
     */
    public static double[] medianFilter(double[] data, int windowSize) {
        if (data == null || data.length == 0) return new double[0];
        double[] result = new double[data.length];
        int half = windowSize / 2;

        for (int i = 0; i < data.length; i++) {
            if (i < half || i >= data.length - half) {
                result[i] = data[i]; // 边缘不处理
                continue;
            }
            double[] window = new double[windowSize];
            System.arraycopy(data, i - half, window, 0, windowSize);
            Arrays.sort(window);
            result[i] = window[half];
        }
        return result;
    }

    /**
     * 2. Savitzky-Golay 平滑滤波 (基于最小二乘多项式拟合)
     * 修复了原算法不准确的问题，采用标准矩阵伪逆求解
     */
    public static double[] sgFilter(double[] data, int windowSize, int degree) {
        if (data == null || data.length <= windowSize) return data;
        if (windowSize % 2 == 0) windowSize += 1; // 确保窗口为奇数

        int m = windowSize / 2;
        // 1. 构建设计矩阵 X
        RealMatrix X = MatrixUtils.createRealMatrix(windowSize, degree + 1);
        for (int i = -m; i <= m; i++) {
            for (int j = 0; j <= degree; j++) {
                X.setEntry(i + m, j, Math.pow(i, j));
            }
        }

        // 2. 计算伪逆矩阵: C = (X^T * X)^-1 * X^T
        RealMatrix XT = X.transpose();
        RealMatrix XTX = XT.multiply(X);
        RealMatrix XTX_inv = new LUDecomposition(XTX).getSolver().getInverse();
        RealMatrix C = XTX_inv.multiply(XT);

        // 3. 获取中间行（对应拟合中心点）的卷积系数
        double[] coeffs = C.getRow(0);

        // 4. 执行卷积平滑
        double[] result = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            if (i < m || i >= data.length - m) {
                result[i] = data[i]; // 边缘保持原样
                continue;
            }
            double sum = 0;
            for (int j = -m; j <= m; j++) {
                sum += data[i + j] * coeffs[j + m];
            }
            result[i] = sum;
        }
        return result;
    }

    /**
     * 3. 累计梯形积分 (Cumulative Trapezoidal Integration)
     */
    public static double[] cumtrapz(double[] y, double dx) {
        if (y == null || y.length == 0) return new double[0];
        double[] res = new double[y.length];
        res[0] = 0;
        for (int i = 1; i < y.length; i++) {
            res[i] = res[i - 1] + ((y[i] + y[i - 1]) / 2.0) * dx;
        }
        return res;
    }

    /**
     * 4. 滑动平均 (Moving Average)
     */
    public static double[] movingAverage(double[] data, int windowSize) {
        if (data == null || data.length == 0) return new double[0];
        double[] res = new double[data.length];
        int half = windowSize / 2;
        for (int i = 0; i < data.length; i++) {
            int start = Math.max(0, i - half);
            int end = Math.min(data.length - 1, i + half);
            double sum = 0;
            for (int j = start; j <= end; j++) {
                sum += data[j];
            }
            res[i] = sum / (end - start + 1);
        }
        return res;
    }

    /**
     * 5. 线性缝隙修正算法 (用于拼接积分分段)
     */
    public static double[] applyLinearFix(double[] arr, double totalFix, boolean isStart) {
        double[] res = new double[arr.length];
        int len = arr.length;
        if (len == 0) return res;
        for (int i = 0; i < len; i++) {
            int dist = isStart ? (len - 1 - i) : i;
            double weight = (double) dist / Math.max(1, len - 1);
            res[i] = arr[i] + totalFix * weight;
        }
        return res;
    }

    /**
     * 6. 数组反转
     */
    public static double[] reverseArray(double[] arr) {
        double[] res = new double[arr.length];
        for(int i=0; i<arr.length; i++) res[i] = arr[arr.length - 1 - i];
        return res;
    }

    /**
     * 7. 数组拼接
     */
    public static double[] concatArrays(double[] a, double[] b) {
        double[] res = new double[a.length + b.length];
        System.arraycopy(a, 0, res, 0, a.length);
        System.arraycopy(b, 0, res, a.length, b.length);
        return res;
    }
}