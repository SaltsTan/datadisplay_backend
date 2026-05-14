package org.jeecg.modules.basic.util;

import org.apache.commons.math3.linear.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 薄板样条插值 (Thin Plate Spline, TPS)
 * 径向基函数: φ(r) = r² * log(r)  (2D情况)
 * 用于在空间散点上生成平滑曲面
 *
 * 当控制点过多时自动降采样以提高性能
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
public class ThinPlateSplineInterpolator {

    private static final int MAX_CONTROL_POINTS = 500;

    private double[] weights;
    private double[] polyCoeffs;
    private double[][] controlPoints;
    private boolean solved;

    public ThinPlateSplineInterpolator() {
        this.solved = false;
    }

    /**
     * 使用离散控制点构建TPS模型
     *
     * @param points 控制点数组，每个点为 [x, y, z]
     */
    public void buildModel(List<double[]> points) {
        if (points == null || points.isEmpty()) {
            this.solved = false;
            return;
        }

        // 去除重复坐标点（相同x,y的点只保留第一个）
        List<double[]> unique = removeDuplicateXY(points);
        List<double[]> sampled = downsample(unique, MAX_CONTROL_POINTS);

        int n = sampled.size();
        if (n < 3) {
            this.solved = false;
            return;
        }

        this.controlPoints = new double[n][2];
        for (int i = 0; i < n; i++) {
            controlPoints[i][0] = sampled.get(i)[0];
            controlPoints[i][1] = sampled.get(i)[1];
        }

        int m = n + 3;
        double[][] A = new double[m][m];
        double[] b = new double[m];

        // 正则化参数（防止矩阵奇异）
        double lambda = 1e-4;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double dx = controlPoints[i][0] - controlPoints[j][0];
                double dy = controlPoints[i][1] - controlPoints[j][1];
                double r = Math.sqrt(dx * dx + dy * dy);
                A[i][j] = tpsKernel(r);
            }
            // 添加正则化项（Tikhonov regularization）
            A[i][i] += lambda;

            A[i][n] = 1.0;
            A[i][n + 1] = controlPoints[i][0];
            A[i][n + 2] = controlPoints[i][1];
            A[n][i] = 1.0;
            A[n + 1][i] = controlPoints[i][0];
            A[n + 2][i] = controlPoints[i][1];
            b[i] = sampled.get(i)[2];
        }

        for (int i = n; i < m; i++) {
            for (int j = n; j < m; j++) {
                A[i][j] = 0.0;
            }
            b[i] = 0.0;
        }

        try {
            RealMatrix matrix = MatrixUtils.createRealMatrix(A);
            RealVector vector = MatrixUtils.createRealVector(b);

            // 优先使用 LU 分解，失败则使用 SVD（更鲁棒）
            RealVector solution;
            try {
                DecompositionSolver solver = new LUDecomposition(matrix).getSolver();
                solution = solver.solve(vector);
            } catch (Exception luEx) {
                // LU 失败，使用 SVD 分解
                DecompositionSolver svdSolver = new SingularValueDecomposition(matrix).getSolver();
                solution = svdSolver.solve(vector);
            }

            this.weights = new double[n];
            System.arraycopy(solution.toArray(), 0, this.weights, 0, n);
            this.polyCoeffs = new double[3];
            System.arraycopy(solution.toArray(), n, this.polyCoeffs, 0, 3);
            this.solved = true;
        } catch (Exception e) {
            System.err.println("TPS求解异常: " + e.getMessage() + ", 控制点数=" + n);
            this.solved = false;
        }
    }

    /**
     * 在二维网格上插值
     *
     * @param gridX X轴采样点
     * @param gridY Y轴采样点
     * @return Z值二维数组 [gridX.length][gridY.length]
     */
    public double[][] interpolate(double[] gridX, double[] gridY) {
        int nx = gridX.length;
        int ny = gridY.length;
        double[][] zGrid = new double[nx][ny];

        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                zGrid[i][j] = evaluate(gridX[i], gridY[j]);
            }
        }
        return zGrid;
    }

    /**
     * 在单个点 (x, y) 上计算插值Z值
     */
    public double evaluate(double x, double y) {
        if (!solved || weights == null || polyCoeffs == null || controlPoints == null) {
            return 0.0;
        }

        double z = polyCoeffs[0] + polyCoeffs[1] * x + polyCoeffs[2] * y;

        for (int i = 0; i < weights.length; i++) {
            double dx = x - controlPoints[i][0];
            double dy = y - controlPoints[i][1];
            double r = Math.sqrt(dx * dx + dy * dy);
            z += weights[i] * tpsKernel(r);
        }

        return z;
    }

    private double tpsKernel(double r) {
        if (r < 1e-12) return 0.0;
        return r * r * Math.log(r);
    }

    private List<double[]> downsample(List<double[]> points, int targetSize) {
        if (points.size() <= targetSize) return new ArrayList<>(points);

        List<double[]> sampled = new ArrayList<>();
        double step = (double) points.size() / targetSize;

        for (int i = 0; i < targetSize; i++) {
            int idx = (int) Math.round(i * step);
            if (idx >= points.size()) idx = points.size() - 1;
            sampled.add(points.get(idx));
        }

        return sampled;
    }

    /**
     * 去除重复坐标点（相同 x,y 的点只保留第一个）
     * 使用容差 0.01 判断坐标是否相同
     */
    private List<double[]> removeDuplicateXY(List<double[]> points) {
        List<double[]> unique = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (double[] p : points) {
            // 用四舍五入到小数点后1位作为key，避免浮点精度问题
            String key = Math.round(p[0] * 10) + "_" + Math.round(p[1] * 10);
            if (seen.add(key)) {
                unique.add(p);
            }
        }
        return unique;
    }

    public boolean isSolved() {
        return solved;
    }
}
