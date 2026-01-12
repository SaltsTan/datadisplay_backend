package org.jeecg.common.excel.support;

import java.util.List;

public interface ExcelImporter<T> {

	/**
	 * 导入数据逻辑
	 *
	 * @param data 数据集合
	 */
	void save(List<T> data);

}
