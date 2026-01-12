package org.jeecg.modules.basic.util;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : cdh
 * @date : 2025/10/24
 */
public class ArrayAverager {

    /**
     * 计算多个数组中对应位置元素的平均值
     *
     * @param arrays 不确定数量的Double数组，每个数组长度必须相同
     * @return 包含每个位置平均值的数组
     */
    public static List<Double> calculateAverage(List<List<Double>>  arrays) {
        // 检查输入参数
        if (arrays == null || arrays.size() == 0) {
            return new ArrayList<>();
        }

        int arrayLength = arrays.get(0).size();

        // 验证所有数组长度一致
        for (List<Double>  array : arrays) {
            if (array == null || array.size() != arrayLength) {
                throw new IllegalArgumentException("所有数组必须具有相同的长度且不能为null");
            }
        }

        // 创建结果数组
        List<Double> result = new ArrayList<>();

        // 对每个位置计算平均值
        for (int i = 0; i < arrayLength; i++) {
            double sum = 0;
            for (List<Double> array : arrays) {
                sum += array.get( i);
            }
            result.add( sum / arrays.size());
        }

        return result;
    }
}
