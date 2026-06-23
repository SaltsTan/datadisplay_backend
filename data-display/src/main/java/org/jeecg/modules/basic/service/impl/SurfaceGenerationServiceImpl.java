package org.jeecg.modules.basic.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.basic.config.DeformationConstants;
import org.jeecg.modules.basic.dto.PointDTO;
import org.jeecg.modules.basic.dto.SkeletonCurveDTO;
import org.jeecg.modules.basic.entity.BasicData;
import org.jeecg.modules.basic.entity.BasicDefaultValue;
import org.jeecg.modules.basic.service.IBasicDataService;
import org.jeecg.modules.basic.service.IBasicDefaultValueService;
import org.jeecg.modules.basic.service.ISensorCalculationService;
import org.jeecg.modules.basic.service.ISurfaceGenerationService;
import org.jeecg.modules.basic.util.*;
import org.jeecg.modules.basic.vo.SurfaceMeshDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 曲面生成核心实现
 * 
 * 处理流程（按照需求文档 docs\软件修改文档.docx）：
 * 1. 调用 calculateSingleRowDeformation 获取已反演的相对变形数据
 * 2. 从反演数据中提取六条骨架曲线并截断/补齐到指定长度
 * 3. 大U横边闭合处理
 * 4. 小U主从基准融合
 * 5. 幽灵节点构建
 * 6. 曲面插值生成
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
@Service
public class SurfaceGenerationServiceImpl implements ISurfaceGenerationService {

    @Autowired
    private ISensorCalculationService sensorCalculationService;
    
    @Autowired
    private IBasicDataService basicDataService;
    
    @Autowired
    private IBasicDefaultValueService basicDefaultValueService;
    
    @Autowired
    private ResourceLoader resourceLoader;
    
    @Autowired
    private ObjectMapper objectMapper;

    private static final String[] TARGET_ROWS = {"19", "20", "21", "22", "23", "24", "25"};
    
    // 排体截断与平移规则 (minX, cutEnds)
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
    
    private static final int SENSOR_SPACING = 1;
    private static final int FILTER_WINDOW = 12;

    @Override
    public SurfaceMeshDTO generateSingleSurface(String time1, String time2, String rowBody) {
        // 重新实现数据处理流程，不调用 calculateSingleRowDeformation
        // 因为需要对每条边应用不同的截取长度
        
        // Step 1: 获取原始波长差数据（从数据库）
        List<PointDTO> rawData = fetchRawDataFromDatabase(rowBody, time1, time2);
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }
        
        // Step 2: 获取基准排体数据（用于后续基准修正）
        List<PointDTO> baselineRaw = fetchRawDataFromDatabase("26", time1, time2);
        List<PointDTO> baseline = applySpatialFiltersForBaseline(baselineRaw, "26");
        
        // Step 3: 空间滤波（改进版：支持每条边不同的截取长度）
        List<PointDTO> spatialFiltered = applySpatialFiltersWithDifferentLengths(rawData, rowBody);
        
        if (spatialFiltered == null || spatialFiltered.isEmpty()) {
            return null;
        }
        
        // Step 4: 信号去噪（中值滤波 + SG滤波）
        applySignalDenoising(spatialFiltered);
        
        // Step 5: 基准修正（计算微应变）
        subtractDiffDataAndCalculateStrain(spatialFiltered, baseline);
        
        // Step 6: 物理反演（形变积分）
        double cFactor = getCFactor(rowBody);
        computeDeformationPhysics(spatialFiltered, rowBody, cFactor);
        
        // Step 7-12: 曲面构建
        return buildSurfaceMesh(rowBody, spatialFiltered);
    }
    
    /**
     * 从数据库获取原始波长差数据
     * 复用 SensorCalculationServiceImpl 中的逻辑
     */
    private List<PointDTO> fetchRawDataFromDatabase(String rowBody, String time1, String time2) {
        List<PointDTO> resultList = new ArrayList<>();
        try {
            // 1. 读取对应 rowNum 的 JSON 坐标配置
            String jsonFilePath = "classpath:sensor-positions/sensor_positions_" + rowBody + ".json";
            Resource resource = resourceLoader.getResource(jsonFilePath);
            if (!resource.exists()) {
                System.err.println("JSON file not found: " + jsonFilePath);
                return resultList;
            }

            JsonNode rootNode;
            try (InputStream is = resource.getInputStream()) {
                rootNode = objectMapper.readTree(is);
            }

            String paitiKey = "排体" + rowBody + "号";
            JsonNode paitiNode = rootNode.get(paitiKey);
            if (paitiNode == null || !paitiNode.has("channels")) {
                System.err.println("Invalid JSON format for " + paitiKey);
                return resultList;
            }

            JsonNode channelsNode = paitiNode.get("channels");

            // 提取需要查询的通道
            List<String> channelNumbers = new ArrayList<>();
            Iterator<String> fieldNames = channelsNode.fieldNames();
            while (fieldNames.hasNext()) {
                String chName = fieldNames.next();
                channelNumbers.add(chName.replace("CH", ""));
            }

            if (channelNumbers.isEmpty()) return resultList;

            // 2. 查询波长数据
            Map<String, List<Double>> waveMap1 = fetchWavelengthData(channelNumbers, time1);
            Map<String, List<Double>> waveMap2 = fetchWavelengthData(channelNumbers, time2);

            // 3. 组装 PointDTO
            Iterator<Map.Entry<String, JsonNode>> channelFields = channelsNode.fields();
            while (channelFields.hasNext()) {
                Map.Entry<String, JsonNode> entry = channelFields.next();
                String chName = entry.getKey();
                String chNum = chName.replace("CH", "");

                JsonNode coordsNode = entry.getValue().get("coordinates");
                if (coordsNode == null || !coordsNode.isArray()) continue;

                List<Double> w1List = waveMap1.getOrDefault(chNum, new ArrayList<>());
                List<Double> w2List = waveMap2.getOrDefault(chNum, new ArrayList<>());

                for (int i = 0; i < coordsNode.size(); i++) {
                    JsonNode coord = coordsNode.get(i);
                    double x = coord.get(0).asDouble();
                    double y = coord.get(1).asDouble();
                    Double lon = coord.size() >= 3 ? coord.get(2).asDouble() : null;
                    Double lat = coord.size() >= 4 ? coord.get(3).asDouble() : null;

                    double val1 = (i < w1List.size()) ? w1List.get(i) : 0.0;
                    double val2 = (i < w2List.size()) ? w2List.get(i) : 0.0;
                    BigDecimal decimal1 = new BigDecimal(val1);
                    BigDecimal decimal2 = new BigDecimal(val2);
                    BigDecimal finalValue = decimal2.subtract(decimal1)
                            .multiply(BigDecimal.TEN.multiply(BigDecimal.TEN.multiply(BigDecimal.TEN)))
                            .divide(new BigDecimal("1.2"), 4, BigDecimal.ROUND_HALF_UP);
                    double diff = finalValue.doubleValue();
                    
                    PointDTO dto = new PointDTO();
                    dto.setRowBody(rowBody);
                    dto.setChannel(chName);
                    dto.setPointIndex(i);
                    dto.setOriginalX(x);
                    dto.setOriginalY(y);
                    dto.setLon(lon);
                    dto.setLat(lat);
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
        Map<String, List<Double>> map = new HashMap<>();
        basicData.forEach(i -> {
            Integer channel = i.getChannel();
            String wavelength = i.getWavelength();
            String[] split = wavelength.split("/");
            List<String> list1 = Arrays.asList(split);
            List<Double> doubleList = list1.stream()
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
            map.put(String.valueOf(channel), doubleList);
        });
        return map;
    }
    
    /**
     * 空间滤波（改进版）：完全复制 calculateSingleRowDeformation 的逻辑
     * 
     * 处理流程：
     * 1. 去除岸上点（originalX < minX）- 直接移除，不参与后续计算
     * 2. 按距离拆分成多个形状（>5m视为断开）
     * 3. 识别大U（最长的一段，且长度>150）
     * 4. 对大U截取端点（靠近岸边的端点截去 cutEnds 个点）- 直接移除
     * 5. 坐标平移
     */
    private List<PointDTO> applySpatialFiltersWithDifferentLengths(List<PointDTO> data, String rowBody) {
        int[] rule = SPATIAL_RULES.getOrDefault(rowBody, new int[]{0, 0});
        int minX = rule[0];  // 岸上点的X坐标阈值
        int cutEnds = rule[1];  // 端点截取数量

        // Step 1: 按通道分组
        Map<String, List<PointDTO>> channelGroups = data.stream()
                .collect(Collectors.groupingBy(PointDTO::getChannel));

        List<PointDTO> result = new ArrayList<>();

        for (Map.Entry<String, List<PointDTO>> entry : channelGroups.entrySet()) {
            List<PointDTO> points = entry.getValue();
            
            // 按点索引排序
            points.sort(Comparator.comparingInt(PointDTO::getPointIndex));

            // Step 2: 去除岸上点（originalX < minX 的点）- 直接移除
            points = points.stream()
                    .filter(p -> p.getOriginalX() >= minX)
                    .collect(Collectors.toList());

            if (points.isEmpty()) continue;

            // Step 3: 按距离拆分成多个形状（>5m视为断开）
            List<List<PointDTO>> shapes = splitIntoShapes(points);

            // Step 4: 识别大U（最长的一段，且长度>150）
            int bigUIndex = -1;
            if (cutEnds > 0) {
                int maxLength = 0;
                for (int i = 0; i < shapes.size(); i++) {
                    if (shapes.get(i).size() > maxLength && shapes.get(i).size() > 150) {
                        maxLength = shapes.get(i).size();
                        bigUIndex = i;
                    }
                }
            }

            // Step 5: 处理每个形状
            for (int i = 0; i < shapes.size(); i++) {
                List<PointDTO> shape = shapes.get(i);
                boolean isBigU = (i == bigUIndex);
                double shiftX = minX;

                if (isBigU) {
                    // 大U：根据端点位置决定是否截取
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

                    // 执行截取 - 直接移除端点
                    if (shape.size() > cutStart + cutEnd) {
                        shape = new ArrayList<>(shape.subList(cutStart, shape.size() - cutEnd));
                    } else {
                        shape = new ArrayList<>(); // 异常数据保护
                    }

                    // 大U特有平移量
                    shiftX += cutEnds;
                }

                // 坐标平移并重置索引
                for (int j = 0; j < shape.size(); j++) {
                    PointDTO p = shape.get(j);
                    p.setPointIndex(j);
                    p.setX(p.getOriginalX() - shiftX);
                    p.setY(p.getOriginalY());
                    result.add(p);
                }
            }
        }

        return result;
    }
    
    
    /**
     * 基准排体的空间滤波（简化版，不需要区分每条边）
     */
    private List<PointDTO> applySpatialFiltersForBaseline(List<PointDTO> data, String rowBody) {
        int[] rule = SPATIAL_RULES.getOrDefault(rowBody, new int[]{0, 0});
        int minX = rule[0];

        List<PointDTO> result = new ArrayList<>();
        
        for (PointDTO p : data) {
            if (p.getOriginalX() >= minX) {
                PointDTO newP = new PointDTO();
                newP.setRowBody(p.getRowBody());
                newP.setChannel(p.getChannel());
                newP.setPointIndex(p.getPointIndex());
                newP.setOriginalX(p.getOriginalX());
                newP.setOriginalY(p.getOriginalY());
                newP.setX(p.getOriginalX() - minX);
                newP.setY(p.getOriginalY());
                newP.setZ(p.getZ());
                result.add(newP);
            }
        }
        
        return result;
    }
    
    /**
     * 信号去噪：中值滤波 + SG滤波
     */
    private void applySignalDenoising(List<PointDTO> data) {
        Map<String, List<PointDTO>> groups = data.stream()
                .collect(Collectors.groupingBy(PointDTO::getChannel));

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
    
    /**
     * 基准修正：计算微应变
     */
    private void subtractDiffDataAndCalculateStrain(List<PointDTO> data, List<PointDTO> baseline) {
        // 建立基准数据的哈希索引
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

            // 微应变 = (当前滤波后波长差 - 基准波长差) / 1.2
            double strain = (p.getZ() - baseZ) / 1.2;
            p.setStrain(Math.round(strain * 100.0) / 100.0);
        }
    }
    
    /**
     * 物理反演：形变积分
     */
    private void computeDeformationPhysics(List<PointDTO> data, String rowBody, double cFactor) {
        List<String> splitRows = Arrays.asList("19", "20", "21", "22", "23", "24", "25");
        boolean shouldSplit = splitRows.contains(rowBody);

        // 对22号排体执行特定的物理通道重新排序缝合
        List<PointDTO> sortedData = reorderDataForRow(data, rowBody);

        List<List<PointDTO>> shapes = splitIntoShapes(sortedData);

        for (int sIdx = 0; sIdx < shapes.size(); sIdx++) {
            List<PointDTO> shape = shapes.get(sIdx);
            double[] strains = shape.stream().mapToDouble(PointDTO::getStrain).toArray();

            // 步骤 1: 去直流偏置
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
                // 三段式双重积分
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

            // 步骤 2: 局部高通滤波
            double[] trendDeformation = MathUtils.movingAverage(rawDeformations, FILTER_WINDOW);
            double[] finalDeformations = new double[rawDeformations.length];
            for (int i = 0; i < rawDeformations.length; i++) {
                finalDeformations[i] = rawDeformations[i] - trendDeformation[i];
            }

            // 步骤 3: 特殊补偿（25号排体右侧）
            if ("25".equals(rowBody)) {
                for (int i = 0; i < finalDeformations.length; i++) {
                    PointDTO p = shape.get(i);
                    if (p.getOriginalX() >= 109 && p.getOriginalX() <= 219 && p.getOriginalY() == 29) {
                        finalDeformations[i] += 120.0;
                    }
                }
            }

            // 回填最终形变值
            for (int i = 0; i < shape.size(); i++) {
                PointDTO p = shape.get(i);
                double defValue = i < finalDeformations.length ? finalDeformations[i] : 0;
                p.setDeformation(Math.round(defValue * 100.0) / 100.0);
                if (sIdx > 0) {
                    p.setChannel(p.getChannel() + "_S" + sIdx);
                }
            }
        }
    }
    
    private double[] calculateRawSegment(double[] detrendedStrains, double cFactor) {
        if (detrendedStrains.length == 0) return new double[0];

        double[] curvature = new double[detrendedStrains.length];
        for (int i = 0; i < detrendedStrains.length; i++) {
            curvature[i] = detrendedStrains[i] * cFactor;
        }

        double[] theta = MathUtils.cumtrapz(curvature, SENSOR_SPACING);
        return MathUtils.cumtrapz(theta, SENSOR_SPACING);
    }
    
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
    
    private int[] findCornerIndices(List<PointDTO> points) {
        if (points.size() < 10) return new int[]{-1, -1};
        int c1 = -1, c2 = -1;

        for (int i = 2; i < points.size() - 2; i++) {
            PointDTO prev = points.get(i - 1);
            PointDTO curr = points.get(i);
            PointDTO next = points.get(i + 1);

            boolean isPrevVert = Math.abs(curr.getOriginalX() - prev.getOriginalX()) > 0.5 
                              && Math.abs(curr.getOriginalY() - prev.getOriginalY()) < 0.5;
            boolean isNextHorz = Math.abs(next.getOriginalX() - curr.getOriginalX()) < 0.5 
                              && Math.abs(next.getOriginalY() - curr.getOriginalY()) > 0.5;

            if (isPrevVert && isNextHorz) { c1 = i; break; }
        }

        int searchStart = c1 != -1 ? c1 + 2 : 2;
        for (int i = searchStart; i < points.size() - 2; i++) {
            PointDTO prev = points.get(i - 1);
            PointDTO curr = points.get(i);
            PointDTO next = points.get(i + 1);

            boolean isPrevHorz = Math.abs(curr.getOriginalX() - prev.getOriginalX()) < 0.5 
                              && Math.abs(curr.getOriginalY() - prev.getOriginalY()) > 0.5;
            boolean isNextVert = Math.abs(next.getOriginalX() - curr.getOriginalX()) > 0.5 
                              && Math.abs(next.getOriginalY() - curr.getOriginalY()) < 0.5;

            if (isPrevHorz && isNextVert) { c2 = i; break; }
        }
        return new int[]{c1, c2};
    }
    
    /**
     * 获取排体的修正系数
     */
    private double getCFactor(String rowBody) {
        double cFactor = -0.05;
        BasicDefaultValue byId = basicDefaultValueService.getOne(
                new LambdaQueryWrapper<BasicDefaultValue>()
                        .eq(BasicDefaultValue::getPaitiNo, rowBody));
        if (ObjectUtil.isNotNull(byId)) {
            cFactor = ObjectUtil.isEmpty(byId.getFactorValue()) 
                    ? -0.05 
                    : byId.getFactorValue().doubleValue();
        }
        return cFactor;
    }

    @Override
    public Map<String, SurfaceMeshDTO> generateAllSurfaces(String time1, String time2) {
        // 直接调用完整的反演流程获取所有排体的形变数据
        Map<String, List<PointDTO>> allData = sensorCalculationService.calculateAllRowsDeformation(time1, time2);
        Map<String, SurfaceMeshDTO> resultMap = new LinkedHashMap<>();

        for (String row : TARGET_ROWS) {
            List<PointDTO> rowData = allData.get(row);
            if (rowData != null && !rowData.isEmpty()) {
                SurfaceMeshDTO mesh = buildSurfaceMesh(row, rowData);
                if (mesh != null) {
                    resultMap.put(row, mesh);
                }
            }
        }
        return resultMap;
    }

    private SurfaceMeshDTO buildSurfaceMesh(String rowBody, List<PointDTO> rowData) {
        SkeletonDataPreparer preparer = new SkeletonDataPreparer();
        List<SkeletonCurveDTO> curves = preparer.extractSkeletonCurves(rowBody, rowData);

        SkeletonCurveDTO largeLeft = findCurve(curves, SkeletonCurveDTO.CurveType.LARGE_LEFT);
        SkeletonCurveDTO largeRight = findCurve(curves, SkeletonCurveDTO.CurveType.LARGE_RIGHT);
        SkeletonCurveDTO largeBottom = findCurve(curves, SkeletonCurveDTO.CurveType.LARGE_BOTTOM);
        SkeletonCurveDTO smallLeft = findCurve(curves, SkeletonCurveDTO.CurveType.SMALL_LEFT);
        SkeletonCurveDTO smallRight = findCurve(curves, SkeletonCurveDTO.CurveType.SMALL_RIGHT);
        SkeletonCurveDTO smallBottom = findCurve(curves, SkeletonCurveDTO.CurveType.SMALL_BOTTOM);

        double[] zLL = safeZ(largeLeft);
        double[] zLR = safeZ(largeRight);
        double[] zLB = safeZ(largeBottom);
        double[] zSL = safeZ(smallLeft);
        double[] zSR = safeZ(smallRight);
        double[] zSB = safeZ(smallBottom);

        int nLarge = DeformationConstants.getLargeULength(rowBody);
        int nSmall = DeformationConstants.getSmallULength(rowBody);

        BigUClosureService bigUClosure = new BigUClosureService();
        double[] zLBclosed = bigUClosure.closeBottom(zLL, zLR, zLB);

        SmallUFusionService smallUFusion = new SmallUFusionService(bigUClosure);
        SmallUFusionService.FusionResult fusionResult = smallUFusion.fuse(zLL, zLR, zSL, zSR, zSB);

        GhostNodeService ghostNodeService = new GhostNodeService();
        List<GhostNodeService.GhostNode> ghostNodes = ghostNodeService.buildGhostNodes(
                zLL, zLR, zLBclosed, nLarge);

        List<double[]> controlPoints = new ArrayList<>();

        for (int i = 0; i < zLL.length; i++) {
            controlPoints.add(new double[]{ (double) i, DeformationConstants.BIG_U_LEFT_Y, zLL[i] });
        }
        for (int i = 0; i < zLR.length; i++) {
            controlPoints.add(new double[]{ (double) i, DeformationConstants.BIG_U_RIGHT_Y, zLR[i] });
        }
        for (int i = 0; i < zLBclosed.length; i++) {
            double y = DeformationConstants.BIG_U_RIGHT_Y
                    - i * (DeformationConstants.BIG_U_RIGHT_Y - DeformationConstants.BIG_U_LEFT_Y)
                    / Math.max(1, zLBclosed.length - 1);
            controlPoints.add(new double[]{ (double)(nLarge - 1), y, zLBclosed[i] });
        }
        for (int i = 0; i < fusionResult.zSmallLeftFused.length; i++) {
            controlPoints.add(new double[]{ (double) i, DeformationConstants.SMALL_U_LEFT_Y,
                    fusionResult.zSmallLeftFused[i] });
        }
        for (int i = 0; i < fusionResult.zSmallRightFused.length; i++) {
            controlPoints.add(new double[]{ (double) i, DeformationConstants.SMALL_U_RIGHT_Y,
                    fusionResult.zSmallRightFused[i] });
        }
        for (int i = 0; i < fusionResult.zSmallBottomClosed.length; i++) {
            double y = DeformationConstants.SMALL_U_RIGHT_Y
                    - i * (DeformationConstants.SMALL_U_RIGHT_Y - DeformationConstants.SMALL_U_LEFT_Y)
                    / Math.max(1, fusionResult.zSmallBottomClosed.length - 1);
            controlPoints.add(new double[]{ (double)(nSmall - 1), y, fusionResult.zSmallBottomClosed[i] });
        }

        for (GhostNodeService.GhostNode gn : ghostNodes) {
            controlPoints.add(new double[]{ gn.x, gn.y, gn.z });
        }

        ThinPlateSplineInterpolator interpolator = new ThinPlateSplineInterpolator();
        interpolator.buildModel(controlPoints);

        double[] gridX = new double[DeformationConstants.GRID_X_STEPS];
        double[] gridY = new double[DeformationConstants.GRID_Y_STEPS];

        for (int i = 0; i < DeformationConstants.GRID_X_STEPS; i++) {
            gridX[i] = DeformationConstants.RAFT_LENGTH * i / (DeformationConstants.GRID_X_STEPS - 1);
        }
        for (int j = 0; j < DeformationConstants.GRID_Y_STEPS; j++) {
            gridY[j] = DeformationConstants.RAFT_WIDTH * j / (DeformationConstants.GRID_Y_STEPS - 1);
        }

        double[][] zGrid;
        if (interpolator.isSolved()) {
            zGrid = interpolator.interpolate(gridX, gridY);
            // 保留2位小数，减少JSON体积
            for (int i = 0; i < zGrid.length; i++) {
                for (int j = 0; j < zGrid[i].length; j++) {
                    zGrid[i][j] = Math.round(zGrid[i][j] * 100.0) / 100.0;
                }
            }
        } else {
            zGrid = new double[DeformationConstants.GRID_X_STEPS][DeformationConstants.GRID_Y_STEPS];
        }

        SurfaceMeshDTO dto = new SurfaceMeshDTO();
        dto.setRowBody(rowBody);
        dto.setGridWidth(DeformationConstants.GRID_X_STEPS);
        dto.setGridHeight(DeformationConstants.GRID_Y_STEPS);
        dto.setXMin(0.0);
        dto.setXMax(DeformationConstants.RAFT_LENGTH);
        dto.setYMin(0.0);
        dto.setYMax(DeformationConstants.RAFT_WIDTH);
        dto.setZGrid(zGrid);

        dto.setLargeULeft(buildTrack(zLL, DeformationConstants.BIG_U_LEFT_Y, 0));
        dto.setLargeURight(buildTrack(zLR, DeformationConstants.BIG_U_RIGHT_Y, 0));

        List<double[]> lbTrack = new ArrayList<>();
        for (int i = 0; i < zLBclosed.length; i++) {
            double y = DeformationConstants.BIG_U_RIGHT_Y
                    - i * (DeformationConstants.BIG_U_RIGHT_Y - DeformationConstants.BIG_U_LEFT_Y)
                    / Math.max(1, zLBclosed.length - 1);
            // X坐标保持为整数，Y坐标根据横边位置计算（可能有小数），Z值保留2位小数
            double x = (double)(nLarge - 1);  // 整数
            y = Math.round(y * 100.0) / 100.0;  // 保留2位小数
            double z = Math.round(zLBclosed[i] * 100.0) / 100.0;
            lbTrack.add(new double[]{ x, y, z });
        }
        dto.setLargeUBottom(lbTrack);

        dto.setSmallULeft(buildTrack(fusionResult.zSmallLeftFused, DeformationConstants.SMALL_U_LEFT_Y, 0));
        dto.setSmallURight(buildTrack(fusionResult.zSmallRightFused, DeformationConstants.SMALL_U_RIGHT_Y, 0));

        List<double[]> sbTrack = new ArrayList<>();
        for (int i = 0; i < fusionResult.zSmallBottomClosed.length; i++) {
            double y = DeformationConstants.SMALL_U_RIGHT_Y
                    - i * (DeformationConstants.SMALL_U_RIGHT_Y - DeformationConstants.SMALL_U_LEFT_Y)
                    / Math.max(1, fusionResult.zSmallBottomClosed.length - 1);
            // X坐标保持为整数，Y坐标根据横边位置计算（可能有小数），Z值保留2位小数
            double x = (double)(nSmall - 1);  // 整数
            y = Math.round(y * 100.0) / 100.0;  // 保留2位小数
            double z = Math.round(fusionResult.zSmallBottomClosed[i] * 100.0) / 100.0;
            sbTrack.add(new double[]{ x, y, z });
        }
        dto.setSmallUBottom(sbTrack);

        dto.setHasBrokenCable("19".equals(rowBody));

        List<Integer> ghostIndices = new ArrayList<>();
        if ("19".equals(rowBody)) {
            int supplementCount = DeformationConstants.P25_LEFT_COUNT - DeformationConstants.P25_RIGHT_COUNT;
            for (int i = 0; i < supplementCount; i++) {
                ghostIndices.add(i);
            }
        }
        dto.setGhostCableIndices(ghostIndices);

        convertZUnitMmToM(dto);

        return dto;
    }

    private void convertZUnitMmToM(SurfaceMeshDTO dto) {
        double[][] zGrid = dto.getZGrid();
        if (zGrid != null) {
            for (int i = 0; i < zGrid.length; i++)
                if (zGrid[i] != null)
                    for (int j = 0; j < zGrid[i].length; j++)
                        zGrid[i][j] = round(zGrid[i][j] / 1000.0);
        }
        convertTrackUnit(dto.getLargeULeft());
        convertTrackUnit(dto.getLargeURight());
        convertTrackUnit(dto.getLargeUBottom());
        convertTrackUnit(dto.getSmallULeft());
        convertTrackUnit(dto.getSmallURight());
        convertTrackUnit(dto.getSmallUBottom());
    }

    private void convertTrackUnit(List<double[]> track) {
        if (track == null) return;
        for (double[] pt : track) {
            if (pt != null && pt.length >= 3) {
                pt[0] = round(pt[0]);
                pt[1] = round(pt[1]);
                pt[2] = round(pt[2] / 1000.0);
            }
        }
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private SkeletonCurveDTO findCurve(List<SkeletonCurveDTO> curves, SkeletonCurveDTO.CurveType type) {
        for (SkeletonCurveDTO c : curves) {
            if (c.getCurveType() == type) return c;
        }
        return null;
    }

    private double[] safeZ(SkeletonCurveDTO curve) {
        if (curve == null || curve.getZValues() == null) return new double[0];
        return curve.getZValues();
    }

    /**
     * 构建光缆轨迹点列表
     * 
     * @param zValues 形变Z值数组
     * @param fixedY 固定的Y坐标（整数）
     * @param startX 起始X坐标（整数）
     * @return 轨迹点列表 [[x, y, z], ...]，其中x和y为整数，z保留2位小数
     */
    private List<double[]> buildTrack(double[] zValues, double fixedY, int startX) {
        List<double[]> track = new ArrayList<>();
        for (int i = 0; i < zValues.length; i++) {
            // X和Y坐标保持为整数（不带小数）
            double x = (double)(startX + i);
            double y = fixedY;  // fixedY本身就是整数（10.0, 14.0, 23.0, 29.0）
            // Z值保留2位小数，减少JSON体积
            double z = Math.round(zValues[i] * 100.0) / 100.0;
            track.add(new double[]{ x, y, z });
        }
        return track;
    }
}
