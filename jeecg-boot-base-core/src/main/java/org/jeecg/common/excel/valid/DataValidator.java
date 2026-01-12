package org.jeecg.common.excel.valid;

public interface DataValidator<T> {
    /**
     * 校验数据是否合法
     * @param data 要校验的数据
     * @return 如果校验不通过，返回错误信息；如果校验通过，返回 null
     */
    String validate(T data);
}