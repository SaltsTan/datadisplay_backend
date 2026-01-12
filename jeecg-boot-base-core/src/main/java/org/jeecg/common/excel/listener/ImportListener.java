package org.jeecg.common.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jeecg.common.excel.support.ExcelImporter;
import org.jeecg.common.excel.valid.DataValidator;

import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ImportListener<T> extends AnalysisEventListener<T> {

    /**
     * 默认每隔3000条存储数据库
     */
    private int batchCount = 3000;
    /**
     * 缓存的数据列表
     */
    private List<T> list = new ArrayList<>();
    /**
     * 错误数据列表
     */
    private List<String> errorList = new ArrayList<>();
    /**
     * 校验数据格式的规则
     */
    private final DataValidator<T> dataValidator;
    /**
     * 数据导入类
     */
    private final ExcelImporter<T> importer;

    @Override
    public void invoke(T data, AnalysisContext analysisContext) {
        // 校验数据是否符合要求
        String errorMessage = dataValidator.validate(data);
        if (errorMessage != null) {
            // 如果数据格式不正确，记录错误信息并跳过该数据
            errorList.add("第" + (analysisContext.readRowHolder().getRowIndex() + 1) + "行数据校验失败: " + errorMessage);
            return;  // 跳过当前数据，不进行后续处理
        }

        list.add(data);
        // 达到BATCH_COUNT，则调用importer方法入库，防止数据几万条数据在内存，容易OOM
        if (list.size() >= batchCount) {
            // 调用importer方法
            importer.save(list);
            // 存储完成清理list
            list.clear();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        // 最后一次保存数据
        if (!list.isEmpty()) {
            importer.save(list);
            list.clear();
        }
        // 如果存在错误数据，返回错误信息
        if (!errorList.isEmpty()) {
            throw new ExcelAnalysisException("数据导入失败，原因如下:\n" + String.join("\n", errorList));
        }
    }

}
