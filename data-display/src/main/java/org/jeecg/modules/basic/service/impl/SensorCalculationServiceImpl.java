package org.jeecg.modules.basic.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.basic.dto.PointDTO;
import org.jeecg.modules.basic.entity.BasicData;
import org.jeecg.modules.basic.entity.BasicDefaultValue;
import org.jeecg.modules.basic.mapper.BasicDataMapper;
import org.jeecg.modules.basic.service.IBasicDefaultValueService;
import org.jeecg.modules.basic.service.ISensorCalculationService;
import org.jeecg.modules.basic.service.IBasicDataService;
import org.jeecg.modules.basic.util.MathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SensorCalculationServiceImpl implements ISensorCalculationService {

    private static final int SENSOR_SPACING = 1;
    //窗口改为12
    private static final int FILTER_WINDOW = 12;
    private static final String BASELINE_ROW = "26";

    @Autowired
    private BasicDataMapper basicDataMapper;

    @Autowired
    private IBasicDataService basicDataService;
    @Autowired
    private IBasicDefaultValueService basicDefaultValueService;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private ObjectMapper objectMapper;

    // 默认排体截断与平移规则 (minX, cutEnds)
    private static final Map<String, int[]> SPATIAL_RULES = new HashMap<>();
    static {
        SPATIAL_RULES.put("25", new int[]{20, 0});
        SPATIAL_RULES.put("24", new int[]{20, 25});
        SPATIAL_RULES.put("23", new int[]{20, 25});
        SPATIAL_RULES.put("22", new int[]{20, 25});
        SPATIAL_RULES.put("21", new int[]{30, 25});
        SPATIAL_RULES.put("20", new int[]{25, 25});
        SPATIAL_RULES.put("19", new int[]{25, 25});
        SPATIAL_RULES.put("26", new int[]{56, 0});
    }

    @Override
    public List<PointDTO> calculateSingleRowDeformation(String time1, String time2, String rowBody) {
        // 1. 获取基准排体(26号)数据
        List<PointDTO> baseline = getProcessedBaseline(time1, time2);

        // 2. 获取并处理目标排体数据
        return processPipeline(rowBody, time1, time2, baseline);
    }

    @Override
    public Map<String, List<PointDTO>> calculateAllRowsDeformation(String time1, String time2) {
        // 1. 获取基准排体(26号)数据，只获取一次以提升性能
        List<PointDTO> baseline = getProcessedBaseline(time1, time2);

        // 2. 遍历处理 19~25 号排体
        String[] targetRows = {"19", "20", "21", "22", "23", "24", "25"};
        Map<String, List<PointDTO>> resultMap = new HashMap<>();

        for (String row : targetRows) {
            List<PointDTO> rowData = processPipeline(row, time1, time2, baseline);
            if (!rowData.isEmpty()) {
                resultMap.put(row, rowData);
            }
        }
        return resultMap;
    }

    /**
     * 核心计算管道：数据获取 -> 空间滤波 -> 去噪 -> 基准修正 -> 形变积分
     */
    private List<PointDTO> processPipeline(String rowNum, String time1, String time2, List<PointDTO> baseline) {
        // Step 1: 从数据库提取合并后的初始波长差数据
        List<PointDTO> rawData = fetchRawDataFromDatabase(rowNum, time1, time2);
        if (rawData.isEmpty()) return rawData;

        // 获取排体的修正系数 C-Factor (前端原逻辑默认为 -0.05)
        double cFactor = -0.05;
        BasicDefaultValue byId = basicDefaultValueService.getOne(new LambdaQueryWrapper<BasicDefaultValue>().eq(BasicDefaultValue::getPaitiNo, rowNum));
        if(ObjectUtil.isNotNull(byId)){
            cFactor=ObjectUtil.isEmpty(byId.getFactorValue()) ?- 0.05 : byId.getFactorValue().doubleValue();
        }
        // Step 2: 空间滤波 (裁剪大U并平移)
        List<PointDTO> spatialFiltered = applySpatialFilters(rawData, rowNum);

        // Step 3: 信号去噪 (中值 + SG)
        applySignalDenoising(spatialFiltered);

        // Step 4: 基准修正 (计算微应变)
        subtractDiffDataAndCalculateStrain(spatialFiltered, baseline);

        // Step 5: 物理反演 (三段式形变积分)
        computeDeformationPhysics(spatialFiltered, rowNum, cFactor);

        return spatialFiltered;
    }

    // ==========================================
    // 内部处理逻辑实现
    // ==========================================

    private List<PointDTO> applySpatialFilters(List<PointDTO> data, String rowNum) {
        int[] rule = SPATIAL_RULES.getOrDefault(rowNum, new int[]{0, 0});
        int minX = rule[0];
        int cutEnds = rule[1];

        Map<String, List<PointDTO>> groups = data.stream().collect(Collectors.groupingBy(PointDTO::getChannel));
        List<PointDTO> result = new ArrayList<>();

        for (List<PointDTO> points : groups.values()) {
            points.sort(Comparator.comparingInt(PointDTO::getPointIndex));

            // 1. MinX 过滤岸边数据
            points = points.stream().filter(p -> p.getOriginalX() >= minX).collect(Collectors.toList());

            // 2. 依据距离拆分形状 (>5m视为断开)
            List<List<PointDTO>> shapes = splitIntoShapes(points);

            // 3. 寻找大U (最长的一段，且【修复：长度需大于150】防止小U被误截)
            int bigUIndex = -1;
            if (cutEnds > 0) {
                int maxLength = 0;
                for (int i = 0; i < shapes.size(); i++) {
                    if (shapes.get(i).size() > maxLength) {
                        maxLength = shapes.get(i).size();
                        bigUIndex = i;
                    }
                }
                if (maxLength < 150) bigUIndex = -1; // 修复小U被误判
            }

            // 4. 处理并平移
            for (int i = 0; i < shapes.size(); i++) {
                List<PointDTO> shape = shapes.get(i);
                boolean isBigU = (i == bigUIndex) && (cutEnds > 0);
                double shiftX = minX;

                if (isBigU) {
                    // 【修复】大U可能由多个通道拼接而成(如CH9和CH10)，此时不能盲目截断首尾
                    // 只有当一段的端点在岸边 (X坐标较小，假设小于100) 时，才执行对应的截断
                    int cutStart = 0;
                    int cutEnd = 0;
                    if (!shape.isEmpty()) {
                        PointDTO firstP = shape.get(0);
                        PointDTO lastP = shape.get(shape.size() - 1);

                        // X坐标小于100被视为靠近岸边，只有靠近岸边的一端才需要被截去cutEnds
                        if (firstP.getOriginalX() < 100) {
                            cutStart = cutEnds;
                        }
                        if (lastP.getOriginalX() < 100) {
                            cutEnd = cutEnds;
                        }
                    }

                    // 执行截取
                    if (shape.size() > cutStart + cutEnd) {
                        shape = shape.subList(cutStart, shape.size() - cutEnd);
                    } else {
                        shape.clear(); // 异常数据保护
                    }

                    // 大U特有平移量：无论是否两端都被截，整个大U必须统一多平移cutEnds以保证拼接处坐标对齐
                    shiftX += cutEnds;
                }

                for (int j = 0; j < shape.size(); j++) {
                    PointDTO p = shape.get(j);
                    p.setPointIndex(j); // 重置索引保证连线不乱
                    p.setX(p.getOriginalX() - shiftX);
                    p.setY(p.getOriginalY());
                    result.add(p);
                }
            }
        }
        return result;
    }


    private void applySignalDenoising(List<PointDTO> data) {
        Map<String, List<PointDTO>> groups = data.stream().collect(Collectors.groupingBy(PointDTO::getChannel));

        for (List<PointDTO> points : groups.values()) {
            double[] rawDiffs = points.stream().mapToDouble(PointDTO::getZ).toArray();

            // 一级去噪：中值滤波
            double[] despiked = MathUtils.medianFilter(rawDiffs, 5);

            // 二级去噪：SG 滤波
            int frameLen = Math.min(21, (despiked.length / 2) * 2 - 1);
            if (frameLen < 3) frameLen = 3;
            double[] smoothed = MathUtils.sgFilter(despiked, frameLen, 3);

            for (int i = 0; i < points.size(); i++) {
                points.get(i).setZ(smoothed[i]);
            }
        }
    }

    private void subtractDiffDataAndCalculateStrain(List<PointDTO> data, List<PointDTO> baseline) {
        // 建立基准数据的哈希索引，使用原始坐标匹配
        Map<String, Double> diffMap = new HashMap<>();
        Map<Double, Double> diffXMap = new HashMap<>();

        for (PointDTO b : baseline) {
            diffMap.put(b.getOriginalX() + "_" + b.getOriginalY(), b.getZ());
            diffXMap.put(b.getOriginalX(), b.getZ());
        }

        for (PointDTO p : data) {
            String key = p.getOriginalX() + "_" + p.getOriginalY();
            Double baseZ = diffMap.get(key);
            if (baseZ == null) baseZ = diffXMap.getOrDefault(p.getOriginalX(), 0.0);

            // 【核心公式】：微应变 = (当前滤波后波长差 - 基准波长差) / 1.2
            double strain = (p.getZ() - baseZ) / 1.2;
            // 保留两位小数
            p.setStrain(Math.round(strain * 100.0) / 100.0);
        }
    }

    private void computeDeformationPhysics(List<PointDTO> data, String rowNum, double cFactor) {
        List<String> splitRows = Arrays.asList("19", "20", "21", "22", "23", "24","25");
        boolean shouldSplit = splitRows.contains(rowNum);

        // 仅对22号排体执行特定的物理通道重新排序缝合
        List<PointDTO> sortedData = reorderDataForRow(data, rowNum);

        List<List<PointDTO>> shapes = splitIntoShapes(sortedData);

        for (int sIdx = 0; sIdx < shapes.size(); sIdx++) {
            List<PointDTO> shape = shapes.get(sIdx);
            double[] strains = shape.stream().mapToDouble(PointDTO::getStrain).toArray();

            // ==========================================
            // 新增：步骤 1. 增加全局去直流偏置步骤 (减去整体的平均值)
            // ==========================================
            if (strains.length > 0) {
                double sumStrain = 0;
                for (double s : strains) {
                    sumStrain += s;
                }
                double meanStrain = sumStrain / strains.length;
                for (int i = 0; i < strains.length; i++) {
                    strains[i] = strains[i] - meanStrain;
                }
            }

            double[] rawDeformations;

            int[] corners = shouldSplit ? findCornerIndices(shape) : new int[]{-1, -1};
            int c1 = corners[0], c2 = corners[1];

            if (c1 > 0 && c2 > c1) {
                // 三段式双重积分 (此时使用的是已经去直流偏置的应变数据)
                double[] defA = calculateRawSegment(Arrays.copyOfRange(strains, 0, c1 + 1), cFactor);
                double[] defB_raw = calculateRawSegment(Arrays.copyOfRange(strains, c1 + 1, c2 + 1), cFactor);

                double offset = defA.length > 0 ? defA[defA.length - 1] : 0;
                double[] defB = Arrays.stream(defB_raw).map(v -> v + offset).toArray();

                double[] strainC = Arrays.copyOfRange(strains, c2 + 1, strains.length);
                double[] revStrainC = MathUtils.reverseArray(strainC);
                double[] defC_raw = calculateRawSegment(revStrainC, cFactor);
                double[] defC = MathUtils.reverseArray(defC_raw);

                double gap = (defB.length > 0 ? defB[defB.length - 1] : 0) - (defC.length > 0 ? defC[0] : 0);

                double[] leftParams = MathUtils.concatArrays(defA, defB);
                double[] fixedLeft = MathUtils.applyLinearFix(leftParams, -gap / 2.0, false);
                double[] fixedRight = MathUtils.applyLinearFix(defC, gap / 2.0, true);

                rawDeformations = MathUtils.concatArrays(fixedLeft, fixedRight);
            } else {
                // 整体双重积分
                rawDeformations = calculateRawSegment(strains, cFactor);
            }

            // ==========================================
            // 修改：步骤 2. 顺序调整，将局部高通滤波操作移至双重积分之后
            // 公式： ∆𝜆h𝑖𝑔h = ∆𝜆𝑟𝑎𝑤 - ∆𝜆𝑡𝑟𝑒𝑛𝑑
            // ==========================================
            double[] trendDeformation = MathUtils.movingAverage(rawDeformations, FILTER_WINDOW);
            double[] finalDeformations = new double[rawDeformations.length];
            for (int i = 0; i < rawDeformations.length; i++) {
                finalDeformations[i] = rawDeformations[i] - trendDeformation[i];
            }

            // ==========================================
            // 新增：步骤 3. 参数与特殊补偿修改 (25号排体右侧断点上移 120mm)
            // ==========================================
            if ("25".equals(rowNum)) {
                for (int i = 0; i < finalDeformations.length; i++) {
                    PointDTO p = shape.get(i);
                    // 精准判断：只对 X 在 109~219 之间，且 Y=29 的右侧特定点位进行上移
                    if (p.getOriginalX() >= 109 && p.getOriginalX() <= 219 && p.getOriginalY() == 29) {
                        finalDeformations[i] += 120.0;
                    }
                }
            }

            // 回填最终计算完成的形变值并重命名多段通道名
            for (int i = 0; i < shape.size(); i++) {
                PointDTO p = shape.get(i);
                double defValue = i < finalDeformations.length ? finalDeformations[i] : 0;
                p.setDeformation(Math.round(defValue * 100.0) / 100.0);
                // 记录拼接后的新通道标识，方便前端高亮和显示
                if (sIdx > 0) {
                    p.setChannel(p.getChannel() + "_S" + sIdx);
                }
            }
        }
    }

    /**
     * 【算法修改配套方法】: 纯粹执行曲率转换与双重积分，不再内部包含高通滤波
     */
    private double[] calculateRawSegment(double[] detrendedStrains, double cFactor) {
        if (detrendedStrains.length == 0) return new double[0];

        double[] curvature = new double[detrendedStrains.length];
        for (int i = 0; i < detrendedStrains.length; i++) {
            curvature[i] = detrendedStrains[i] * cFactor;
        }

        // 第一次积分：倾角
        double[] theta = MathUtils.cumtrapz(curvature, SENSOR_SPACING);
        // 第二次积分：相对变形量
        return MathUtils.cumtrapz(theta, SENSOR_SPACING);
    }



    private double getDistance(PointDTO p1, PointDTO p2) {
        return Math.hypot(p2.getOriginalX() - p1.getOriginalX(), p2.getOriginalY() - p1.getOriginalY());
    }

    /**
     * 数据预排序算法（针对特定的排体合并物理连线）：
     * 1. 对于常规排体：按照通道分组，避免不同通道数据交叉，输出一个平展的 List 供后续根据物理距离截断。
     * 2. 对于 22 号排体：单独抽出 CH10 和 CH9。排在最前并验证 CH9 离 CH10 哪一端最近，以此判定是否要反转 CH9 原始顺序。
     */
    private List<PointDTO> reorderDataForRow(List<PointDTO> data, String rowNum) {
        Map<String, List<PointDTO>> channelGroups = data.stream()
                .collect(Collectors.groupingBy(PointDTO::getChannel));


        List<PointDTO> result = new ArrayList<>();

        if ("22".equals(rowNum)) {
            List<PointDTO> ch10 = channelGroups.remove("CH10");
            List<PointDTO> ch9 = channelGroups.remove("CH9");

            if (ch10 != null && !ch10.isEmpty()) {
                result.addAll(ch10);
            }

            if (ch10 != null && !ch10.isEmpty() && ch9 != null && !ch9.isEmpty()) {
                Collections.reverse(ch9);
            }

            if (ch9 != null && !ch9.isEmpty()) {
                result.addAll(ch9);
            }

        } else {
            return data;
        }

        return result;
    }

    private List<List<PointDTO>> splitIntoShapes(List<PointDTO> points) {
        List<List<PointDTO>> shapes = new ArrayList<>();
        if (points.isEmpty()) return shapes;

        List<PointDTO> current = new ArrayList<>();
        current.add(points.get(0));

        for (int i = 1; i < points.size(); i++) {
            PointDTO p1 = points.get(i - 1);
            PointDTO p2 = points.get(i);
            double dist = Math.hypot(p2.getOriginalX() - p1.getOriginalX(), p2.getOriginalY() - p1.getOriginalY());
            if (dist > 5.0) {
                shapes.add(current);
                current = new ArrayList<>();
            }
            current.add(p2);
        }
        shapes.add(current);
        return shapes;
    }

    private int[] findCornerIndices(List<PointDTO> points) {
        if (points.size() < 10) return new int[]{-1, -1};
        int c1 = -1, c2 = -1;

        for (int i = 2; i < points.size() - 2; i++) {
            PointDTO prev = points.get(i - 1);
            PointDTO curr = points.get(i);
            PointDTO next = points.get(i + 1);

            boolean isPrevVert = Math.abs(curr.getOriginalX() - prev.getOriginalX()) > 0.5 && Math.abs(curr.getOriginalY() - prev.getOriginalY()) < 0.5;
            boolean isNextHorz = Math.abs(next.getOriginalX() - curr.getOriginalX()) < 0.5 && Math.abs(next.getOriginalY() - curr.getOriginalY()) > 0.5;

            if (isPrevVert && isNextHorz) { c1 = i; break; }
        }

        int searchStart = c1 != -1 ? c1 + 2 : 2;
        for (int i = searchStart; i < points.size() - 2; i++) {
            PointDTO prev = points.get(i - 1);
            PointDTO curr = points.get(i);
            PointDTO next = points.get(i + 1);

            boolean isPrevHorz = Math.abs(curr.getOriginalX() - prev.getOriginalX()) < 0.5 && Math.abs(curr.getOriginalY() - prev.getOriginalY()) > 0.5;
            boolean isNextVert = Math.abs(next.getOriginalX() - curr.getOriginalX()) > 0.5 && Math.abs(next.getOriginalY() - curr.getOriginalY()) < 0.5;

            if (isPrevHorz && isNextVert) { c2 = i; break; }
        }
        return new int[]{c1, c2};
    }

    // ==========================================
    // 数据获取与组装区
    // ==========================================

    private List<PointDTO> getProcessedBaseline(String time1, String time2) {
        // 获取26号排体作为基准，并仅进行空间匹配滤波
        List<PointDTO> raw26 = fetchRawDataFromDatabase(BASELINE_ROW, time1, time2);
        return applySpatialFilters(raw26, BASELINE_ROW);
    }

    /**
     * 读取对应 rowNum 的 JSON 坐标配置，查询数据库，组装 PointDTO
     */
    private List<PointDTO> fetchRawDataFromDatabase(String rowNum, String time1, String time2) {
        List<PointDTO> resultList = new ArrayList<>();
        try {
            // 1. 读取对应 rowNum 的 JSON 坐标配置
            String jsonFilePath = "classpath:sensor-positions/sensor_positions_" + rowNum + ".json";
            Resource resource = resourceLoader.getResource(jsonFilePath);
            if (!resource.exists()) {
                System.err.println("JSON file not found: " + jsonFilePath);
                return resultList;
            }

            JsonNode rootNode;
            try (InputStream is = resource.getInputStream()) {
                rootNode = objectMapper.readTree(is);
            }

            // JSON 结构形如: {"排体23号": {"channels": {"CH15": {"coordinates": [[0,10], ...]}}}}
            String paitiKey = "排体" + rowNum + "号";
            JsonNode paitiNode = rootNode.get(paitiKey);
            if (paitiNode == null || !paitiNode.has("channels")) {
                System.err.println("Invalid JSON format for " + paitiKey);
                return resultList;
            }

            JsonNode channelsNode = paitiNode.get("channels");

            // 提取需要查询的通道 (如 "15", "14")
            List<String> channelNumbers = new ArrayList<>();
            Iterator<String> fieldNames = channelsNode.fieldNames();
            while (fieldNames.hasNext()) {
                String chName = fieldNames.next();
                channelNumbers.add(chName.replace("CH", ""));
            }

            if (channelNumbers.isEmpty()) return resultList;

            // 2. 调用 basicDataService 查询 time1 和 time2 该排体所有通道的波长数据
            Map<String, List<Double>> waveMap1 = fetchWavelengthData(channelNumbers, time1);
            Map<String, List<Double>> waveMap2 = fetchWavelengthData(channelNumbers, time2);

            // 3. 组装 PointDTO
            Iterator<Map.Entry<String, JsonNode>> channelFields = channelsNode.fields();
            while (channelFields.hasNext()) {
                Map.Entry<String, JsonNode> entry = channelFields.next();
                String chName = entry.getKey(); // e.g. "CH15"
                String chNum = chName.replace("CH", "");

                JsonNode coordsNode = entry.getValue().get("coordinates");
                if (coordsNode == null || !coordsNode.isArray()) continue;

                List<Double> w1List = waveMap1.getOrDefault(chNum, new ArrayList<>());
                List<Double> w2List = waveMap2.getOrDefault(chNum, new ArrayList<>());

                for (int i = 0; i < coordsNode.size(); i++) {
                    JsonNode coord = coordsNode.get(i);
                    double x = coord.get(0).asDouble();
                    double y = coord.get(1).asDouble();

                    // 【新增】：读取经纬度，如果 JSON 中扩充了 [x, y, lon, lat] 则读取第3和第4个元素
                    Double lon = coord.size() >= 3 ? coord.get(2).asDouble() : null;
                    Double lat = coord.size() >= 4 ? coord.get(3).asDouble() : null;

                    double val1 = (i < w1List.size()) ? w1List.get(i) : 0.0;
                    double val2 = (i < w2List.size()) ? w2List.get(i) : 0.0;
                    BigDecimal decimal1 = new BigDecimal(val1);
                    BigDecimal decimal2 = new BigDecimal(val2);
                    BigDecimal finalValue = decimal2.subtract(decimal1).multiply(BigDecimal.TEN.multiply(BigDecimal.TEN.multiply(BigDecimal.TEN))).divide(new BigDecimal("1.2"),4,BigDecimal.ROUND_HALF_UP);
                    double diff = finalValue.doubleValue();
                    PointDTO dto = new PointDTO();
                    dto.setRowBody(rowNum);
                    dto.setChannel(chName);
                    dto.setPointIndex(i);
                    dto.setOriginalX(x);
                    dto.setOriginalY(y);
                    dto.setLon(lon); // 注入经度
                    dto.setLat(lat); // 注入纬度
                    dto.setZ(diff);

                    resultList.add(dto);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 调用 basicDataService 获取波长数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<Double>> fetchWavelengthData(List<String> channelNumbers, String time) {
        List<BasicData> basicData = basicDataService.channelDataList(channelNumbers, time);
        Map<String, List<Double>> map=new HashMap<>();
        basicData.forEach(i->{
            Integer channel = i.getChannel();
            String wavelength = i.getWavelength();
            String[] split = wavelength.split("/");
            List<String> list1 = Arrays.asList(split);
            List<Double> doubleList = list1.stream()
                    .map(Double::parseDouble)  // 将每个 String 转换为 Double
                    .collect(Collectors.toList());
            map.put(String.valueOf(channel),doubleList);
        });
        return map;
    }
}