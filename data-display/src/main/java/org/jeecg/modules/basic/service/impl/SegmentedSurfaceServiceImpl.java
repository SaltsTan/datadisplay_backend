package org.jeecg.modules.basic.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.basic.config.CableLengthConfig;
import org.jeecg.modules.basic.config.DeformationConstants;
import org.jeecg.modules.basic.dto.PointDTO;
import org.jeecg.modules.basic.dto.SensorRawDataDTO;
import org.jeecg.modules.basic.dto.DeformationSeriesDTO;
import org.jeecg.modules.basic.entity.BasicData;
import org.jeecg.modules.basic.entity.BasicDefaultValue;
import org.jeecg.modules.basic.service.IBasicDataService;
import org.jeecg.modules.basic.service.IBasicDefaultValueService;
import org.jeecg.modules.basic.service.ISegmentedSurfaceService;
import org.jeecg.modules.basic.util.BigUClosureService;
import org.jeecg.modules.basic.util.EdgeSplitter;
import org.jeecg.modules.basic.util.GhostNodeService;
import org.jeecg.modules.basic.util.MathUtils;
import org.jeecg.modules.basic.util.SmallUFusionService;
import org.jeecg.modules.basic.util.ThinPlateSplineInterpolator;
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
 * 分段曲面生成服务实现
 * <p>
 * 处理流程（分段管道）：
 * 1. 从数据库获取原始波长差数据，结合 JSON 坐标文件组装 PointDTO 列表
 * 2. 岸边过滤：移除 originalX < minX 的点
 * 3. 形状段拆分：按相邻点距离>5m断开
 * 4. 边分割：识别大U和小U的6条边
 * 5. 长度调整：截取或补齐到目标长度
 * 6. 信号去噪 + 基准修正 + 物理反演
 * 7. 曲面构建
 *
 * @author Senior Developer
 * @date 2026-05-13
 */
@Service
public class SegmentedSurfaceServiceImpl implements ISegmentedSurfaceService {

    @Autowired
    private IBasicDataService basicDataService;

    @Autowired
    private IBasicDefaultValueService basicDefaultValueService;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String[] TARGET_ROWS = {"19", "20", "21", "22", "23", "24", "25"};

    /**
     * 排体空间规则配置 (minX, cutEnds)
     * minX: 岸边过滤阈值，originalX < minX 的点将被移除
     * cutEnds: 大U端点截取数量
     */
    private static final Map<String, int[]> SPATIAL_RULES = new HashMap<>();
    static {
        SPATIAL_RULES.put("19", new int[]{25, 25});
        SPATIAL_RULES.put("20", new int[]{25, 25});
        SPATIAL_RULES.put("21", new int[]{30, 25});
        SPATIAL_RULES.put("22", new int[]{20, 25});
        SPATIAL_RULES.put("23", new int[]{20, 25});
        SPATIAL_RULES.put("24", new int[]{20, 25});
        SPATIAL_RULES.put("25", new int[]{20, 0});
        SPATIAL_RULES.put("26", new int[]{56, 0});
    }

    // ==================== 接口方法（存根，将在 Task 3.6 中实现） ====================

    @Override
    public SurfaceMeshDTO generateSegmentedSurface(String time1, String time2, String rowBody) {
        try {
            // Step 1: 获取原始数据（波长差值）
            List<PointDTO> rawData = fetchRawData(rowBody, time1, time2);
            if (rawData == null || rawData.isEmpty()) {
                return null;
            }

            // Step 1.5: 获取基准数据（26号排体）并减去基准波长差
            List<PointDTO> baselineRaw = fetchRawData("26", time1, time2);
            if (baselineRaw != null && !baselineRaw.isEmpty()) {
                subtractBaselineData(rawData, baselineRaw);
            }

            // Step 2: 岸边过滤
            List<PointDTO> filtered = applyShoreFilter(rawData, rowBody);
            if (filtered == null || filtered.isEmpty()) {
                return null;
            }

            // Step 2.5: 通道重排序
            if ("22".equals(rowBody)) {
                // 22号：大U跨越CH10+CH9，CH10正序在前，CH9反转后接在后面
                filtered = reorderForRow22(filtered);
            } else if ("25".equals(rowBody)) {
                // 25号：小U跨越CH4+CH1，CH4正序在前，CH1反转后接在后面
                filtered = reorderForRow25(filtered);
            }

            // Step 3: 边分割
            EdgeSplitter.EdgeResult edges = EdgeSplitter.split(filtered, rowBody);

            // Step 4: 提取各边Z值数组（波长差值）
            double[] rawLL = extractZValues(edges.largeULeft);
            double[] rawLR = extractZValues(edges.largeURight);
            double[] rawLB = extractZValues(edges.largeUBottom);
            double[] rawSL = extractZValues(edges.smallULeft);
            double[] rawSR = extractZValues(edges.smallURight);
            double[] rawSB = extractZValues(edges.smallUBottom);

            // Step 5: 长度调整（横边不调整，传null作为edgeType）
            double[] adjLL = adjustEdgeLength(rawLL, rowBody, CableLengthConfig.CableEdge.LARGE_U_LEFT);
            double[] adjLR = adjustEdgeLength(rawLR, rowBody, CableLengthConfig.CableEdge.LARGE_U_RIGHT);
            double[] adjLB = rawLB; // 横边不调整
            double[] adjSL = adjustEdgeLength(rawSL, rowBody, CableLengthConfig.CableEdge.SMALL_U_LEFT);
            double[] adjSR = adjustEdgeLength(rawSR, rowBody, CableLengthConfig.CableEdge.SMALL_U_RIGHT);
            double[] adjSB = rawSB; // 横边不调整

            // Step 5.5: 25号断裂检测（检查调整前的原始长度）
            boolean isRow25Broken = "25".equals(rowBody) && rawLR.length > 0
                    && rawLR.length <= DeformationConstants.P25_RIGHT_COUNT;

            // Step 6: 获取 C-Factor
            double cFactor = getCFactor(rowBody);

            // Step 7: 对每条边执行：转微应变 → 反演 → 去噪平滑
            // 大U左侧（195个点正常反演）
            double[] strainLL = convertToStrain(adjLL);
            double[] defLL = invertEdge(strainLL, rowBody, CableLengthConfig.CableEdge.LARGE_U_LEFT, cFactor);
            double[] zLL = applyDenoising(defLL);

            // 大U右侧
            double[] zLR;
            if (isRow25Broken) {
                // 断裂：跳过长度调整，直接反演原始数据，再补偿到195点
                double[] strainLR = convertToStrain(rawLR);
                double[] defLR = invertEdge(strainLR, rowBody, CableLengthConfig.CableEdge.LARGE_U_RIGHT, cFactor);
                zLR = repairRow25Deformation(zLL, applyDenoising(defLR));
            } else {
                double[] strainLR = convertToStrain(adjLR);
                double[] defLR = invertEdge(strainLR, rowBody, CableLengthConfig.CableEdge.LARGE_U_RIGHT, cFactor);
                zLR = applyDenoising(defLR);
            }

            // 大U横边
            double[] strainLB = convertToStrain(adjLB);
            double[] defLB = invertEdge(strainLB, rowBody, null, cFactor);
            double[] zLB = applyDenoising(defLB);

            // 小U左侧
            double[] strainSL = convertToStrain(adjSL);
            double[] defSL = invertEdge(strainSL, rowBody, CableLengthConfig.CableEdge.SMALL_U_LEFT, cFactor);
            double[] zSL = applyDenoising(defSL);

            // 小U右侧
            double[] strainSR = convertToStrain(adjSR);
            double[] defSR = invertEdge(strainSR, rowBody, CableLengthConfig.CableEdge.SMALL_U_RIGHT, cFactor);
            double[] zSR = applyDenoising(defSR);

            // 小U横边
            double[] strainSB = convertToStrain(adjSB);
            double[] defSB = invertEdge(strainSB, rowBody, null, cFactor);
            double[] zSB = applyDenoising(defSB);

            // Step 8: 曲面构建（zLL, zLR 等已经是去噪后的形变值）
            return buildSurface(rowBody, zLL, zLR, zLB, zSL, zSR, zSB);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Map<String, SurfaceMeshDTO> generateAllSegmentedSurfaces(String time1, String time2) {
        Map<String, SurfaceMeshDTO> results = new LinkedHashMap<>();
        for (String rowBody : TARGET_ROWS) {
            try {
                SurfaceMeshDTO mesh = generateSegmentedSurface(time1, time2, rowBody);
                if (mesh != null) {
                    results.put(rowBody, mesh);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return results;
    }

    @Override
    public Map<String, List<SensorRawDataDTO>> exportStrainData(String time1, String time2) {
        Map<String, List<SensorRawDataDTO>> results = new LinkedHashMap<>();
        for (String rowBody : TARGET_ROWS) {
            try {
                List<SensorRawDataDTO> rowData = exportSingleRowStrainData(time1, time2, rowBody);
                if (rowData != null && !rowData.isEmpty()) {
                    results.put(rowBody, rowData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return results;
    }

    /**
     * 导出单个排体截取后的原始波长和应变数据
     * <p>
     * 流程：
     * 1. 读取 JSON 坐标文件获取通道和坐标
     * 2. 查询 time1 和 time2 的原始波长数据
     * 3. 岸边过滤 + 22号通道重排序
     * 4. 边分割（EdgeSplitter）
     * 5. 对每条边的每个点位，输出原始波长、波长差值、应变值
     * 输出顺序：大U右侧→大U横边→大U左侧→小U右侧→小U横边→小U左侧
     */
    private List<SensorRawDataDTO> exportSingleRowStrainData(String time1, String time2, String rowBody) {
        List<SensorRawDataDTO> resultList = new ArrayList<>();
        try {
            // 1. 读取 JSON 坐标配置
            String jsonFilePath = "classpath:sensor-positions/sensor_positions_" + rowBody + ".json";
            Resource resource = resourceLoader.getResource(jsonFilePath);
            if (!resource.exists()) return resultList;

            JsonNode rootNode;
            try (InputStream is = resource.getInputStream()) {
                rootNode = objectMapper.readTree(is);
            }

            String paitiKey = "排体" + rowBody + "号";
            JsonNode paitiNode = rootNode.get(paitiKey);
            if (paitiNode == null || !paitiNode.has("channels")) return resultList;

            JsonNode channelsNode = paitiNode.get("channels");
            List<String> channelNumbers = new ArrayList<>();
            Iterator<String> fieldNames = channelsNode.fieldNames();
            while (fieldNames.hasNext()) {
                channelNumbers.add(fieldNames.next().replace("CH", ""));
            }
            if (channelNumbers.isEmpty()) return resultList;

            // 2. 查询两个时间点的原始波长数据
            Map<String, List<Double>> waveMap1 = fetchWavelengthData(channelNumbers, time1);
            Map<String, List<Double>> waveMap2 = fetchWavelengthData(channelNumbers, time2);

            // 3. 组装 PointDTO，同时缓存原始波长和经纬度
            List<PointDTO> rawData = new ArrayList<>();
            // key: "channel_pointIndex" → [wave1, wave2]
            Map<String, double[]> waveCache = new HashMap<>();
            // key: "channel_pointIndex" → [lon, lat]（可能为null）
            Map<String, double[]> latlonCache = new HashMap<>();

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
                    double diff = (val2 - val1) * 1000.0 / 1.2;

                    PointDTO dto = new PointDTO();
                    dto.setRowBody(rowBody);
                    dto.setChannel(chName);
                    dto.setPointIndex(i);
                    dto.setOriginalX(x);
                    dto.setOriginalY(y);
                    dto.setLon(lon);
                    dto.setLat(lat);
                    dto.setZ(diff);
                    rawData.add(dto);

                    String cacheKey = chName + "_" + i;
                    waveCache.put(cacheKey, new double[]{val1, val2});
                    if (lon != null && lat != null) {
                        latlonCache.put(cacheKey, new double[]{lon, lat});
                    }
                }
            }

            if (rawData.isEmpty()) return resultList;

            // 4. 岸边过滤
            List<PointDTO> filtered = applyShoreFilter(rawData, rowBody);
            if (filtered == null || filtered.isEmpty()) return resultList;

            // 通道重排序
            if ("22".equals(rowBody)) {
                // 22号：大U跨越CH10+CH9，CH10正序在前，CH9反转后接在后面
                filtered = reorderForRow22(filtered);
            } else if ("25".equals(rowBody)) {
                // 25号：小U跨越CH4+CH1，CH4正序在前，CH1反转后接在后面
                filtered = reorderForRow25(filtered);
            }

            // 5. 边分割（与形变展示一致）
            EdgeSplitter.EdgeResult edges = EdgeSplitter.split(filtered, rowBody);

            // 6. 长度调整（与形变展示一致，补齐点位留空）
            List<PointDTO> adjLR = adjustEdgePoints(edges.largeURight, rowBody, CableLengthConfig.CableEdge.LARGE_U_RIGHT);
            List<PointDTO> adjLL = adjustEdgePoints(edges.largeULeft, rowBody, CableLengthConfig.CableEdge.LARGE_U_LEFT);
            List<PointDTO> adjSR = adjustEdgePoints(edges.smallURight, rowBody, CableLengthConfig.CableEdge.SMALL_U_RIGHT);
            List<PointDTO> adjSL = adjustEdgePoints(edges.smallULeft, rowBody, CableLengthConfig.CableEdge.SMALL_U_LEFT);
            // 横边不调整
            List<PointDTO> adjLB = edges.largeUBottom;
            List<PointDTO> adjSB = edges.smallUBottom;

            // 7. 输出顺序：大U右侧→大U横边→大U左侧→小U右侧→小U横边→小U左侧
            addEdgeExportData(resultList, adjLR, "大U右侧", rowBody, waveCache, latlonCache);
            addEdgeExportData(resultList, adjLB, "大U横边", rowBody, waveCache, latlonCache);
            addEdgeExportData(resultList, adjLL, "大U左侧", rowBody, waveCache, latlonCache);
            addEdgeExportData(resultList, adjSR, "小U右侧", rowBody, waveCache, latlonCache);
            addEdgeExportData(resultList, adjSB, "小U横边", rowBody, waveCache, latlonCache);
            addEdgeExportData(resultList, adjSL, "小U左侧", rowBody, waveCache, latlonCache);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;
    }

    /**
     * 对一条边的点位列表做长度调整（截取或补齐），与形变展示保持一致
     * 截取：取尾部（高X端）目标长度个点
     * 补齐：在首端插入 null 占位点（导出时留空）
     */
    private List<PointDTO> adjustEdgePoints(List<PointDTO> edgePoints, String rowBody, CableLengthConfig.CableEdge edgeType) {
        int targetLength = CableLengthConfig.getTargetLength(rowBody, edgeType);
        if (edgePoints == null) edgePoints = new ArrayList<>();
        int actual = edgePoints.size();

        if (actual == targetLength) return edgePoints;

        if (actual > targetLength) {
            // 截取：取尾部 targetLength 个点
            return new ArrayList<>(edgePoints.subList(actual - targetLength, actual));
        } else {
            // 补齐：首端插入 null 占位点
            int padCount = targetLength - actual;
            List<PointDTO> result = new ArrayList<>();
            for (int i = 0; i < padCount; i++) {
                result.add(null); // null 表示补齐的虚拟点，导出时留空
            }
            result.addAll(edgePoints);
            return result;
        }
    }

    /**
     * 将一条边的点位数据转换为导出 DTO 列表
     * null 点位（补齐的虚拟点）直接跳过，不输出
     */
    private void addEdgeExportData(List<SensorRawDataDTO> resultList, List<PointDTO> edgePoints,
                                    String edgeName, String rowBody,
                                    Map<String, double[]> waveCache,
                                    Map<String, double[]> latlonCache) {
        if (edgePoints == null || edgePoints.isEmpty()) return;
        int outputIndex = 0;
        for (PointDTO p : edgePoints) {
            if (p == null) continue;  // 跳过补齐的虚拟点

            String cacheKey = p.getChannel() + "_" + p.getPointIndex();
            double[] waves = waveCache.getOrDefault(cacheKey, new double[]{0, 0});
            double[] latlon = latlonCache.get(cacheKey);

            SensorRawDataDTO dto = new SensorRawDataDTO();
            dto.setRowBody(rowBody);
            dto.setEdgeName(edgeName);
            dto.setPointIndex(outputIndex++);
            dto.setChannel(p.getChannel());
            dto.setOriginalX(p.getOriginalX());
            dto.setOriginalY(p.getOriginalY());
            dto.setWavelength1(waves[0]);
            dto.setWavelength2(waves[1]);
            double diff = waves[1] - waves[0];
            dto.setWavelengthDiff(Math.round(diff * 10000.0) / 10000.0);
            double strain = diff * 1000.0 / 1.2;
            dto.setStrain(Math.round(strain * 100.0) / 100.0);
            if (latlon != null) {
                dto.setLon(latlon[0]);
                dto.setLat(latlon[1]);
            }
            resultList.add(dto);
        }
    }

    // ==================== 形变时间序列导出 ====================

    @Override
    public DeformationSeriesDTO exportDeformationSeries(String time1, String time2) {
        DeformationSeriesDTO result = new DeformationSeriesDTO();

        // 1. 生成时间序列（time1次日12:00 到 time2当天12:00）
        List<String> timePoints = generateDailyNoonTimePoints(time1, time2);
        result.setTimePoints(timePoints);

        if (timePoints.isEmpty()) {
            result.setData(new LinkedHashMap<>());
            return result;
        }

        // 2. 对每个排体，计算每个时间点的形变
        Map<String, DeformationSeriesDTO.RowDeformationData> dataMap = new LinkedHashMap<>();

        for (String rowBody : TARGET_ROWS) {
            try {
                DeformationSeriesDTO.RowDeformationData rowData = computeRowDeformationSeries(time1, rowBody, timePoints);
                if (rowData != null && rowData.getPoints() != null && !rowData.getPoints().isEmpty()) {
                    dataMap.put(rowBody, rowData);
                }
            } catch (Exception e) {
                System.err.println("排体" + rowBody + "形变序列计算失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        result.setData(dataMap);
        return result;
    }

    /**
     * 生成每天12:00的时间点列表
     * 从 time1 次日12:00 开始，到 time2 当天12:00 结束
     */
    private List<String> generateDailyNoonTimePoints(String time1, String time2) {
        List<String> timePoints = new ArrayList<>();
        try {
            java.time.LocalDateTime dt1 = java.time.LocalDateTime.parse(time1,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            java.time.LocalDateTime dt2 = java.time.LocalDateTime.parse(time2,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 从 time1 次日12:00 开始
            java.time.LocalDate startDate = dt1.toLocalDate().plusDays(1);
            java.time.LocalDate endDate = dt2.toLocalDate();

            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            java.time.LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                java.time.LocalDateTime noon = current.atTime(12, 0, 0);
                timePoints.add(noon.format(fmt));
                current = current.plusDays(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return timePoints;
    }

    /**
     * 计算单个排体在多个时间点的形变序列
     * 每个时间点都和 time1 做形变计算（复用 generateSegmentedSurface 的结果）
     */
    private DeformationSeriesDTO.RowDeformationData computeRowDeformationSeries(
            String time1, String rowBody, List<String> timePoints) {

        DeformationSeriesDTO.RowDeformationData rowData = new DeformationSeriesDTO.RowDeformationData();

        // 先用第一个时间点计算一次，获取点位信息结构
        SurfaceMeshDTO firstMesh = generateSegmentedSurface(time1, timePoints.get(0), rowBody);
        if (firstMesh == null) return null;

        // 提取点位信息（从6条轨迹中）
        List<DeformationSeriesDTO.PointInfo> points = new ArrayList<>();
        List<List<double[]>> allTracks = new ArrayList<>();
        String[] edgeNames = {"大U左侧", "大U右侧", "大U横边", "小U左侧", "小U右侧", "小U横边"};
        List<double[]>[] trackArrays = new List[]{
                firstMesh.getLargeULeft(), firstMesh.getLargeURight(), firstMesh.getLargeUBottom(),
                firstMesh.getSmallULeft(), firstMesh.getSmallURight(), firstMesh.getSmallUBottom()
        };

        // 构建 (X, Y) → (lon, lat) 映射表（从 JSON 坐标文件读取）
        Map<String, double[]> coordLatlonMap = buildCoordLatlonMap(rowBody);

        // 记录每条边的起始索引和长度
        List<int[]> edgeRanges = new ArrayList<>(); // [startIdx, length]
        int totalPoints = 0;

        for (int e = 0; e < 6; e++) {
            List<double[]> track = trackArrays[e];
            if (track == null || track.isEmpty()) {
                edgeRanges.add(new int[]{totalPoints, 0});
                continue;
            }
            int startIdx = totalPoints;
            for (int i = 0; i < track.size(); i++) {
                double[] pt = track.get(i);
                DeformationSeriesDTO.PointInfo info = new DeformationSeriesDTO.PointInfo();
                info.setEdgeName(edgeNames[e]);
                info.setChannel("");
                info.setPointIndex(i);
                info.setOriginalX(pt[0]);
                info.setOriginalY(pt[1]);
                // 查找经纬度
                String coordKey = pt[0] + "_" + pt[1];
                double[] latlon = coordLatlonMap.get(coordKey);
                if (latlon != null) {
                    info.setLon(latlon[0]);
                    info.setLat(latlon[1]);
                }
                points.add(info);
                totalPoints++;
            }
            edgeRanges.add(new int[]{startIdx, track.size()});
        }

        rowData.setPoints(points);

        // 初始化形变二维数组 [点位数][时间点数]
        List<List<Double>> deformations = new ArrayList<>();
        for (int i = 0; i < totalPoints; i++) {
            List<Double> row = new ArrayList<>();
            for (int t = 0; t < timePoints.size(); t++) {
                row.add(0.0);
            }
            deformations.add(row);
        }

        // 对每个时间点计算形变
        for (int t = 0; t < timePoints.size(); t++) {
            String timePoint = timePoints.get(t);
            SurfaceMeshDTO mesh;
            if (t == 0) {
                mesh = firstMesh; // 第一个已经算过了
            } else {
                mesh = generateSegmentedSurface(time1, timePoint, rowBody);
            }

            if (mesh == null) continue;

            // 提取各边的Z值
            List<double[]>[] tracks = new List[]{
                    mesh.getLargeULeft(), mesh.getLargeURight(), mesh.getLargeUBottom(),
                    mesh.getSmallULeft(), mesh.getSmallURight(), mesh.getSmallUBottom()
            };

            for (int e = 0; e < 6; e++) {
                int[] range = edgeRanges.get(e);
                int startIdx = range[0];
                int length = range[1];
                List<double[]> track = tracks[e];
                if (track == null) continue;

                int copyLen = Math.min(length, track.size());
                for (int i = 0; i < copyLen; i++) {
                    double z = track.get(i).length >= 3 ? track.get(i)[2] : 0.0;
                    deformations.get(startIdx + i).set(t, z);
                }
            }
        }

        rowData.setDeformations(deformations);
        return rowData;
    }

    // ==================== 数据获取 ====================

    /**
     * 从数据库获取原始波长差数据，结合 JSON 坐标文件组装 PointDTO 列表
     * <p>
     * 复用 SurfaceGenerationServiceImpl.fetchRawDataFromDatabase 的逻辑模式：
     * 1. 读取对应排体的 JSON 坐标配置文件
     * 2. 提取通道列表并查询两个时间点的波长数据
     * 3. 计算波长差值（(wave2 - wave1) * 1000 / 1.2）并组装 PointDTO
     *
     * @param rowBody 排体号
     * @param time1   基准时间
     * @param time2   当前时间
     * @return 原始 PointDTO 列表
     */
    List<PointDTO> fetchRawData(String rowBody, String time1, String time2) {
        List<PointDTO> resultList = new ArrayList<>();
        try {
            // 1. 读取对应排体的 JSON 坐标配置
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

            // 提取需要查询的通道号
            List<String> channelNumbers = new ArrayList<>();
            Iterator<String> fieldNames = channelsNode.fieldNames();
            while (fieldNames.hasNext()) {
                String chName = fieldNames.next();
                channelNumbers.add(chName.replace("CH", ""));
            }

            if (channelNumbers.isEmpty()) return resultList;

            // 2. 查询两个时间点的波长数据
            Map<String, List<Double>> waveMap1 = fetchWavelengthData(channelNumbers, time1);
            Map<String, List<Double>> waveMap2 = fetchWavelengthData(channelNumbers, time2);

            // 3. 组装 PointDTO（计算波长差值）
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

                    // 计算波长差值: (wave2 - wave1) * 1000 / 1.2
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
     *
     * @param channelNumbers 通道号列表
     * @param time           时间点
     * @return Map<通道号, 波长值列表>
     */
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

    // ==================== 岸边过滤 ====================

    /**
     * 岸边过滤：按通道独立执行，移除 originalX < minX 的点，并按距离>5m拆分为形状段
     * <p>
     * 处理流程：
     * 1. 按通道分组
     * 2. 对每个通道：移除 originalX < minX 的点
     * 3. 按 pointIndex 升序排列
     * 4. 按相邻点欧氏距离>5m拆分为独立形状段
     *
     * @param data    原始 PointDTO 列表
     * @param rowBody 排体号
     * @return 过滤后的 PointDTO 列表（保留 originalX >= minX 的点，按通道独立处理）
     */
    List<PointDTO> applyShoreFilter(List<PointDTO> data, String rowBody) {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        int[] rule = SPATIAL_RULES.getOrDefault(rowBody, new int[]{0, 0});
        int minX = rule[0];

        // Step 1: 按通道分组
        Map<String, List<PointDTO>> channelGroups = data.stream()
                .collect(Collectors.groupingBy(PointDTO::getChannel));

        List<PointDTO> result = new ArrayList<>();

        for (Map.Entry<String, List<PointDTO>> entry : channelGroups.entrySet()) {
            List<PointDTO> points = entry.getValue();

            // Step 2: 按 pointIndex 升序排列
            points.sort(Comparator.comparingInt(PointDTO::getPointIndex));

            // Step 3: 移除 originalX < minX 的点（保留 originalX >= minX）
            List<PointDTO> filtered = points.stream()
                    .filter(p -> p.getOriginalX() >= minX)
                    .collect(Collectors.toList());

            if (filtered.isEmpty()) continue;

            // Step 4: 按相邻点欧氏距离>5m拆分为形状段
            List<List<PointDTO>> shapes = splitIntoShapes(filtered);

            // 将所有形状段的点加入结果（保持形状段内的顺序）
            for (List<PointDTO> shape : shapes) {
                result.addAll(shape);
            }
        }

        return result;
    }

    /**
     * 按相邻点欧氏距离>5m断开为多个形状段
     *
     * @param points 已排序的点列表
     * @return 形状段列表
     */
    private List<List<PointDTO>> splitIntoShapes(List<PointDTO> points) {
        List<List<PointDTO>> shapes = new ArrayList<>();
        if (points == null || points.isEmpty()) return shapes;

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

    // ==================== 分段截取与长度调整 ====================

    /**
     * 对单条边的数据执行长度调整（截取或补齐），使输出数组长度等于目标长度。
     * <p>
     * 规则：
     * - edgeType 为 null（横边 LARGE_U_BOTTOM / SMALL_U_BOTTOM）：不调整，直接返回原数组
     * - 实际长度 > 目标长度：尾部截取（取最后 targetLength 个元素，丢弃首端低X坐标端）
     * - 实际长度 < 目标长度：首端补齐（用前3点平均斜率线性外推）
     * - 实际长度 == 目标长度：直接返回原数组
     * - 边数据为空（长度0）：返回全零数组（长度=目标长度）
     *
     * @param edgeData 单条边的 Z 值数组
     * @param rowBody  排体号
     * @param edgeType 边类型（nullable，null 表示横边不调整）
     * @return 调整后的 double[] 数组，长度等于目标长度
     */
    double[] adjustEdgeLength(double[] edgeData, String rowBody, CableLengthConfig.CableEdge edgeType) {
        // 横边不执行长度调整，直接返回原数据
        if (edgeType == null) {
            return edgeData;
        }

        int targetLength = CableLengthConfig.getTargetLength(rowBody, edgeType);

        // 边数据为空时返回全零数组
        if (edgeData == null || edgeData.length == 0) {
            return new double[targetLength];
        }

        int actualLength = edgeData.length;

        if (actualLength == targetLength) {
            // 实际长度等于目标长度：直接返回原数组
            return edgeData;
        } else if (actualLength > targetLength) {
            // 实际长度 > 目标长度：尾部截取（取最后 targetLength 个元素）
            double[] result = new double[targetLength];
            System.arraycopy(edgeData, actualLength - targetLength, result, 0, targetLength);
            return result;
        } else {
            // 实际长度 < 目标长度：首端补齐（线性外推）
            int padCount = targetLength - actualLength;
            double[] result = new double[targetLength];

            // 计算前3个点的平均斜率用于线性外推
            double avgSlope = computeAverageSlope(edgeData);

            // 从首端向前外推填充补齐值
            // edgeData[0] 对应 result[padCount]，向前外推
            for (int i = 0; i < padCount; i++) {
                // 距离 edgeData[0] 的步数为 (padCount - i)
                int stepsBack = padCount - i;
                result[i] = edgeData[0] - avgSlope * stepsBack;
            }

            // 复制原始数据到后半部分
            System.arraycopy(edgeData, 0, result, padCount, actualLength);
            return result;
        }
    }

    /**
     * 计算数组前3个点（索引0、1、2）的相邻差值平均斜率。
     * <p>
     * 斜率 = ((data[1]-data[0]) + (data[2]-data[1])) / 2 = (data[2]-data[0]) / 2
     * 若数据不足3个点，则使用可用点计算斜率；仅1个点时斜率为0。
     *
     * @param data 输入数组
     * @return 平均斜率
     */
    private double computeAverageSlope(double[] data) {
        if (data == null || data.length <= 1) {
            return 0.0;
        }
        if (data.length == 2) {
            return data[1] - data[0];
        }
        // 使用前3个点：slope = ((data[1]-data[0]) + (data[2]-data[1])) / 2
        return (data[2] - data[0]) / 2.0;
    }



    /**
     * 判断25号排体大U右侧是否处于断裂状态
     * <p>
     * 断裂判定条件：右侧边数据点数不超过111个（P19_RIGHT_COUNT）。
     *
     * @param rightSideData 右侧边原始数据数组
     * @return true 表示断裂状态（点数 <= 111），false 表示正常
     */
    boolean isRow25BrokenCable(double[] rightSideData) {
        if (rightSideData == null || rightSideData.length == 0) {
            return true;
        }
        return rightSideData.length <= DeformationConstants.P25_RIGHT_COUNT;
    }

    /**
     * 25号排体大U右侧断裂形变补偿
     * <p>
     * 在形变值阶段进行补偿（反演+去噪之后）：
     * 1. 左侧195个点已完成反演+去噪，得到完整形变曲线
     * 2. 右侧仅111个点已完成反演+去噪，得到部分形变曲线
     * 3. 补偿方式：
     *    - 右侧前84个点 = 左侧前84个形变值（直接复制）
     *    - 右侧后111个点 = 右侧原始111个形变值 + 左侧第84个点（索引83）的形变值
     * 4. 最终右侧长度 = 84 + 111 = 195
     * <p>
     * 公式：Z_R(x) = Z_R_Tail(x) + Z_R_Virtual(末端)
     * 其中 Z_R_Virtual(末端) = zLeft[83]（左侧第84个点的形变值）
     *
     * @param zLeft  左侧形变值数组（长度195，已完成反演+去噪）
     * @param zRight 右侧形变值数组（长度111，已完成反演+去噪）
     * @return 补偿后的右侧形变值数组，长度195
     */
    private double[] repairRow25Deformation(double[] zLeft, double[] zRight) {
        final int TARGET_LENGTH = DeformationConstants.LARGE_LEN_195; // 195
        final int PADDING_COUNT = TARGET_LENGTH - DeformationConstants.P25_RIGHT_COUNT; // 84

        // 如果右侧为空，返回全零
        if (zRight == null || zRight.length == 0) {
            return new double[TARGET_LENGTH];
        }

        // 如果左侧不足84个点，无法补偿，返回原始数据补零
        if (zLeft == null || zLeft.length < PADDING_COUNT) {
            double[] result = new double[TARGET_LENGTH];
            System.arraycopy(zRight, 0, result, PADDING_COUNT, Math.min(zRight.length, TARGET_LENGTH - PADDING_COUNT));
            return result;
        }

        double[] result = new double[TARGET_LENGTH];

        // 前84个点：复制左侧前84个形变值
        System.arraycopy(zLeft, 0, result, 0, PADDING_COUNT);

        // 补偿偏移量：左侧第84个点（索引83）的形变值
        double compensationOffset = zLeft[PADDING_COUNT - 1];

        // 后111个点：右侧原始形变值 + 补偿偏移量
        int rightCopyLength = Math.min(zRight.length, TARGET_LENGTH - PADDING_COUNT);
        for (int i = 0; i < rightCopyLength; i++) {
            result[PADDING_COUNT + i] = zRight[i] + compensationOffset;
        }

        return result;
    }

    /**
     * 构建补齐数据源（从左侧边数据中提取或外推）
     * <p>
     * 如果左侧数据足够84个点，直接取前84个点。
     * 如果左侧数据不足84个点，使用全部可用点并以前3个有效值的平均斜率线性外推补齐至84个点。
     * 外推方向：从左侧数据的第一个点向前（索引更小的方向）外推填充缺口。
     *
     * @param leftSideData 左侧边数据
     * @param paddingCount 需要的补齐点数（84）
     * @return 长度为 paddingCount 的补齐数据数组
     */
    private double[] buildPaddingSource(double[] leftSideData, int paddingCount) {
        // 左侧数据为空时返回全零
        if (leftSideData == null || leftSideData.length == 0) {
            return new double[paddingCount];
        }

        // 左侧数据足够 paddingCount 个点，直接取前 paddingCount 个
        if (leftSideData.length >= paddingCount) {
            double[] padding = new double[paddingCount];
            System.arraycopy(leftSideData, 0, padding, 0, paddingCount);
            return padding;
        }

        // 左侧数据不足 paddingCount 个点：使用全部可用点 + 线性外推补齐
        int availableCount = leftSideData.length;
        int gapCount = paddingCount - availableCount;

        // 计算前3个有效值的平均斜率（用于向前外推）
        double avgSlope = computeAverageSlope(leftSideData);

        // 构建补齐数组：先放外推值，再放可用的左侧数据
        // 外推方向：从左侧数据的第一个点向前（索引更小的方向）外推
        double[] padding = new double[paddingCount];

        // 后部分放置可用的左侧数据（索引 gapCount ~ paddingCount-1）
        System.arraycopy(leftSideData, 0, padding, gapCount, availableCount);

        // 前部分使用线性外推填充（从 leftSideData[0] 向前推）
        double startValue = leftSideData[0];
        for (int i = gapCount - 1; i >= 0; i--) {
            int stepsBack = gapCount - i;
            padding[i] = startValue - avgSlope * stepsBack;
        }

        return padding;
    }

    /**
     * 从 JSON 坐标文件构建 (X, Y) → (lon, lat) 映射表
     * key 格式：originalX + "_" + originalY
     */
    private Map<String, double[]> buildCoordLatlonMap(String rowBody) {
        Map<String, double[]> map = new HashMap<>();
        try {
            String jsonFilePath = "classpath:sensor-positions/sensor_positions_" + rowBody + ".json";
            Resource resource = resourceLoader.getResource(jsonFilePath);
            if (!resource.exists()) return map;

            JsonNode rootNode;
            try (InputStream is = resource.getInputStream()) {
                rootNode = objectMapper.readTree(is);
            }

            JsonNode channelsNode = rootNode.path("排体" + rowBody + "号").path("channels");
            if (channelsNode.isMissingNode()) return map;

            Iterator<JsonNode> chIter = channelsNode.elements();
            while (chIter.hasNext()) {
                JsonNode ch = chIter.next();
                JsonNode coords = ch.get("coordinates");
                if (coords == null || !coords.isArray()) continue;
                for (JsonNode coord : coords) {
                    if (coord.size() >= 4) {
                        double x = coord.get(0).asDouble();
                        double y = coord.get(1).asDouble();
                        double lon = coord.get(2).asDouble();
                        double lat = coord.get(3).asDouble();
                        map.put(x + "_" + y, new double[]{lon, lat});
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    // ==================== 辅助方法 ====================

    /**
     * 22号排体通道重排序
     * <p>
     * 22号排体的大U光缆跨越CH10和CH9两个通道，需要特殊排序：
     * CH10 在前（正序），CH9 反转后接在后面，形成连续的U形。
     * 其他通道（小U）保持原样追加在后面。
     *
     * @param data 过滤后的点列表
     * @return 重排序后的点列表
     */
    private List<PointDTO> reorderForRow22(List<PointDTO> data) {
        Map<String, List<PointDTO>> channelGroups = data.stream()
                .collect(Collectors.groupingBy(PointDTO::getChannel));

        List<PointDTO> result = new ArrayList<>();

        // CH10 在前（正序）
        List<PointDTO> ch10 = channelGroups.remove("CH10");
        if (ch10 != null && !ch10.isEmpty()) {
            result.addAll(ch10);
        }

        // CH9 反转后接在后面
        List<PointDTO> ch9 = channelGroups.remove("CH9");
        if (ch9 != null && !ch9.isEmpty()) {
            if (ch10 != null && !ch10.isEmpty()) {
                Collections.reverse(ch9);
            }
            result.addAll(ch9);
        }

        // 其他通道（小U等）保持原样追加
        for (List<PointDTO> remaining : channelGroups.values()) {
            result.addAll(remaining);
        }

        return result;
    }

    /**
     * 25号排体通道重排序
     * <p>
     * 25号排体的小U光缆跨越CH4和CH1两个通道：
     * - CH4：Y=23→20（右侧→横边中段），正序在前
     * - CH1：Y=14→19（左侧→横边中段），反转后接在后面
     * 拼接后形成从右到左的连续小U形。
     * 大U（CH3+CH2）和其他通道保持原样。
     *
     * @param data 过滤后的点列表
     * @return 重排序后的点列表
     */
    private List<PointDTO> reorderForRow25(List<PointDTO> data) {
        Map<String, List<PointDTO>> channelGroups = data.stream()
                .collect(Collectors.groupingBy(PointDTO::getChannel));

        List<PointDTO> result = new ArrayList<>();

        // 大U相关通道（CH3、CH2）保持原样先放入
        List<PointDTO> ch3 = channelGroups.remove("CH3");
        if (ch3 != null && !ch3.isEmpty()) result.addAll(ch3);

        List<PointDTO> ch2 = channelGroups.remove("CH2");
        if (ch2 != null && !ch2.isEmpty()) result.addAll(ch2);

        // 小U：CH4 正序在前，CH1 反转后接在后面
        List<PointDTO> ch4 = channelGroups.remove("CH4");
        List<PointDTO> ch1 = channelGroups.remove("CH1");

        if (ch4 != null && !ch4.isEmpty()) {
            result.addAll(ch4);
        }
        if (ch1 != null && !ch1.isEmpty()) {
            if (ch4 != null && !ch4.isEmpty()) {
                Collections.reverse(ch1);
            }
            result.addAll(ch1);
        }

        // 其他剩余通道追加
        for (List<PointDTO> remaining : channelGroups.values()) {
            result.addAll(remaining);
        }

        return result;
    }

    /**
     * 从 PointDTO 列表中提取 Z 值数组
     *
     * @param points 点列表
     * @return Z 值数组，如果输入为空则返回空数组
     */
    private double[] extractZValues(List<PointDTO> points) {
        if (points == null || points.isEmpty()) {
            return new double[0];
        }
        return points.stream().mapToDouble(PointDTO::getZ).toArray();
    }

    /**
     * 从当前排体数据中减去基准排体（26号）的波长差值
     * <p>
     * 匹配规则：根据 originalX + "_" + originalY 作为坐标键进行匹配，
     * 如果找不到精确匹配，则尝试仅按 originalX 匹配。
     *
     * @param data     当前排体的 PointDTO 列表（会被原地修改）
     * @param baseline 基准排体（26号）的 PointDTO 列表
     */
    private void subtractBaselineData(List<PointDTO> data, List<PointDTO> baseline) {
        // 建立基准数据的哈希索引
        Map<String, Double> baselineMap = new HashMap<>();
        Map<Double, Double> baselineXMap = new HashMap<>();

        for (PointDTO b : baseline) {
            baselineMap.put(b.getOriginalX() + "_" + b.getOriginalY(), b.getZ());
            baselineXMap.put(b.getOriginalX(), b.getZ());
        }

        // 遍历当前数据，减去基准值
        for (PointDTO p : data) {
            String key = p.getOriginalX() + "_" + p.getOriginalY();
            Double baseZ = baselineMap.get(key);
            if (baseZ == null) {
                // 尝试仅按X坐标匹配
                baseZ = baselineXMap.getOrDefault(p.getOriginalX(), 0.0);
            }
            // 减去基准波长差
            p.setZ(p.getZ() - baseZ);
        }
    }

    /**
     * 将波长差值转换为微应变
     * <p>
     * fetchRawData 中已经计算了 (wave2-wave1)*1000/1.2，输出已经是微应变。
     * subtractBaselineData 减去基准后仍然是微应变差值。
     * 此方法仅做保留2位小数的处理。
     *
     * @param data 波长差值数组（已经是微应变）
     * @return 微应变数组（保留2位小数）
     */
    private double[] convertToStrain(double[] data) {
        if (data == null || data.length == 0) {
            return new double[0];
        }
        double[] strains = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            strains[i] = Math.round(data[i] * 100.0) / 100.0;
        }
        return strains;
    }

    // ==================== 分段独立反演计算管道 ====================

    /**
     * 信号去噪：中值滤波(window=5) + SG滤波(window=min(21, auto), 多项式阶数3)
     * <p>
     * 处理流程：
     * 1. 中值滤波去除飞点野值（窗口大小5）
     * 2. Savitzky-Golay 平滑滤波（窗口大小为 min(21, (数据长度/2)*2-1) 且不小于3，多项式阶数3）
     *
     * @param data 输入数据数组
     * @return 去噪后的数据数组（长度与输入相同）
     */
    double[] applyDenoising(double[] data) {
        if (data == null || data.length == 0) {
            return new double[0];
        }

        // 一级去噪：中值滤波（窗口大小5）
        double[] despiked = MathUtils.medianFilter(data, 5);

        // 二级去噪：SG 滤波
        int frameLen = Math.min(21, (despiked.length / 2) * 2 - 1);
        if (frameLen < 3) frameLen = 3;
        double[] smoothed = MathUtils.sgFilter(despiked, frameLen, 3);

        return smoothed;
    }

    /**
     * 基准修正：计算微应变
     * <p>
     * 公式：strain = (data[i] - baseline[i]) / 1.2，结果保留2位小数。
     * 如果 baseline 为 null 或长度不足，缺失位置使用 0.0 作为基准值。
     *
     * @param data     当前边的滤波后数据数组
     * @param baseline 基准排体对应位置的数据数组（可为 null）
     * @return 微应变数组（保留2位小数）
     */
    double[] applyBaselineCorrection(double[] data, double[] baseline) {
        if (data == null || data.length == 0) {
            return new double[0];
        }

        double[] strains = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            double baseZ = 0.0;
            if (baseline != null && i < baseline.length) {
                baseZ = baseline[i];
            }
            double strain = (data[i] - baseZ) / 1.2;
            strains[i] = Math.round(strain * 100.0) / 100.0;
        }
        return strains;
    }

    /** 累计梯形积分步长（传感器间距） */
    private static final int SENSOR_SPACING = 1;

    /** 高通滤波移动平均窗口大小 */
    private static final int FILTER_WINDOW = 12;

    /**
     * 对单条边执行物理反演：去直流偏置 → 曲率计算 → 双重积分 → 高通滤波
     * <p>
     * 在分段管道中，边已由 EdgeSplitter 预先拆分，因此所有边均使用整体双重积分。
     * 三段式积分仅在原始整体管道（SurfaceGenerationServiceImpl）中对未拆分的大U使用。
     * <p>
     * <b>数据流转</b>：<br>
     * 输入：微应变数组（波长差值 / 1.2）<br>
     * 输出：形变值数组（通过双重积分从微应变推算）
     *
     * @param strains  微应变数组
     * @param rowBody  排体号
     * @param edgeType 边类型（LARGE_U_LEFT, LARGE_U_RIGHT, SMALL_U_LEFT, SMALL_U_RIGHT, 或 null 表示横边）
     * @param cFactor  C-Factor 修正系数
     * @return 形变值数组（单位：mm）
     */
    double[] invertEdge(double[] strains, String rowBody, CableLengthConfig.CableEdge edgeType, double cFactor) {
        if (strains == null || strains.length == 0) {
            return new double[0];
        }

        // Step 1: 去直流偏置 - 减去算术平均值
        double sum = 0;
        for (double s : strains) {
            sum += s;
        }
        double mean = sum / strains.length;
        double[] dcRemoved = new double[strains.length];
        for (int i = 0; i < strains.length; i++) {
            dcRemoved[i] = strains[i] - mean;
        }

        // Step 2: 曲率计算 - 乘以 cFactor
        double[] curvature = new double[dcRemoved.length];
        for (int i = 0; i < dcRemoved.length; i++) {
            curvature[i] = dcRemoved[i] * cFactor;
        }

        // Step 3: 整体双重积分（边已由 EdgeSplitter 预先拆分，无需三段式积分）
        double[] theta = MathUtils.cumtrapz(curvature, SENSOR_SPACING);
        double[] rawDeformations = MathUtils.cumtrapz(theta, SENSOR_SPACING);

        // Step 4: 高通滤波 - 减去移动平均趋势分量
        double[] trend = MathUtils.movingAverage(rawDeformations, FILTER_WINDOW);
        double[] finalDeformations = new double[rawDeformations.length];
        for (int i = 0; i < rawDeformations.length; i++) {
            finalDeformations[i] = rawDeformations[i] - trend[i];
        }

        return finalDeformations;
    }

    /**
     * 从数据库 BasicDefaultValue 表获取排体对应的 C-Factor 修正系数
     * <p>
     * 查询 BasicDefaultValue 表中 paitiNo 等于 rowBody 的记录，
     * 返回其 factorValue 字段值。若无记录或值为空，返回默认值 -0.05。
     *
     * @param rowBody 排体号
     * @return C-Factor 修正系数
     */
    double getCFactor(String rowBody) {
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

    // ==================== 横边闭合计算 ====================

    /** 小U横边微观细节权重 */
    private static final double SMALL_U_DETAIL_WEIGHT = 0.2;

    /** 小U横边滑动平均窗口大小（用于提取微观细节） */
    private static final int SMALL_U_BOTTOM_MA_WINDOW = 5;

    /**
     * 小U横边新算法：基准线（左右尾端线性插值） + 加权微观细节
     * <p>
     * 步骤：
     * 1. 基准线：用 zSRFused[end] 和 zSLFused[end] 做线性插值（方向从右到左）
     *    Base_SB[i] = Z_SR_final(end) + (Z_SL_final(end) - Z_SR_final(end)) * ratio
     * 2. 提取细节：Detail_SB = zSB - movmean(zSB, 5)，去掉漂移只保留微观起伏
     * 3. 加权叠加：Z_SB_final[i] = Base_SB[i] + Detail_SB[i] * 0.2
     *
     * @param zSLFused 小U左侧融合后的形变值
     * @param zSRFused 小U右侧融合后的形变值
     * @param zSB      小U横边原始反演形变值（方向从右到左）
     * @return 闭合后的小U横边形变值
     */
    private double[] buildSmallUBottom(double[] zSLFused, double[] zSRFused, double[] zSB) {
        if (zSB == null || zSB.length == 0) return new double[0];

        int n = zSB.length;
        double[] result = new double[n];

        // 获取左右尾端值
        double zSREnd = (zSRFused != null && zSRFused.length > 0) ? zSRFused[zSRFused.length - 1] : 0.0;
        double zSLEnd = (zSLFused != null && zSLFused.length > 0) ? zSLFused[zSLFused.length - 1] : 0.0;

        // Step 1: 生成基准线（从右端到左端线性插值）
        double[] baseSB = new double[n];
        for (int i = 0; i < n; i++) {
            double ratio = n > 1 ? (double) i / (n - 1) : 0;
            baseSB[i] = zSREnd + (zSLEnd - zSREnd) * ratio;
        }

        // Step 2: 提取微观细节（原始值 - 滑动平均趋势）
        double[] localTrend = MathUtils.movingAverage(zSB, SMALL_U_BOTTOM_MA_WINDOW);
        double[] detailSB = new double[n];
        for (int i = 0; i < n; i++) {
            detailSB[i] = zSB[i] - localTrend[i];
        }

        // Step 3: 加权叠加（基准线 + 微观细节 × 权重）
        for (int i = 0; i < n; i++) {
            result[i] = baseSB[i] + detailSB[i] * SMALL_U_DETAIL_WEIGHT;
        }

        return result;
    }

    /**
     * 大U横边闭合计算
     * <p>
     * 横边反演方向固定从右到左，起点拼接在右侧尾端（Z_LR[end]），
     * 终点通过误差线性分配接近左侧尾端（Z_LL[end]），使三条线近似连接在一起。
     * <p>
     * 公式：Z_LB[i] = Z_LB[i] - Z_LB[0] + Z_LR[end] + (Error / (length-1)) × i
     * <p>
     * 其中：
     * - Target_Diff = Z_LL[end] - Z_LR[end]（大U左尾端与右尾端的真实高差）
     * - Current_Diff = Z_LB[end] - Z_LB[0]（横边自身高度差）
     * - Error = Target_Diff - Current_Diff（闭合误差）
     *
     * @param zLeft   大U左侧边Z值 (Z_LL)
     * @param zRight  大U右侧边Z值 (Z_LR)
     * @param zBottom 大U横底边Z值 (Z_LB)，方向从右到左
     * @return 闭合修正后的横边Z值
     */
    private double[] closeBigUBottom(double[] zLeft, double[] zRight, double[] zBottom) {
        if (zBottom == null || zBottom.length == 0) return new double[0];

        int n = zBottom.length;
        double[] result = new double[n];

        if (zLeft == null || zLeft.length == 0 || zRight == null || zRight.length == 0) {
            System.arraycopy(zBottom, 0, result, 0, n);
            return result;
        }

        // 目标高度差：左侧尾端 - 右侧尾端
        double targetDiff = zLeft[zLeft.length - 1] - zRight[zRight.length - 1];
        // 横边自身高度差
        double currentDiff = zBottom[n - 1] - zBottom[0];
        // 闭合误差
        double error = targetDiff - currentDiff;

        // 右侧尾端值（横边起点拼接位置）
        double zRightEnd = zRight[zRight.length - 1];

        // 公式：Z_LB[i] = Z_LB[i] - Z_LB[0] + Z_LR[end] + (Error / (length-1)) × i
        double zLB0 = zBottom[0];
        for (int i = 0; i < n; i++) {
            double linearCorrection = n > 1 ? (error / (n - 1)) * i : 0;
            result[i] = zBottom[i] - zLB0 + zRightEnd + linearCorrection;
        }

        return result;
    }

    // ==================== 分段结果组合与曲面构建 ====================

    /**
     * 将6条边的反演结果组合，通过TPS插值构建完整曲面
     *
     * @param rowBody 排体号
     * @param zLL     大U左侧边反演Z值
     * @param zLR     大U右侧边反演Z值
     * @param zLB     大U横底边反演Z值
     * @param zSL     小U左侧边反演Z值
     * @param zSR     小U右侧边反演Z值
     * @param zSB     小U横底边反演Z值
     * @return 曲面网格DTO
     */
    SurfaceMeshDTO buildSurface(String rowBody, double[] zLL, double[] zLR, double[] zLB,
                                double[] zSL, double[] zSR, double[] zSB) {

        // Step 1: Null/empty protection - 用零数组替换null
        int largeTarget = DeformationConstants.getLargeULength(rowBody);
        int smallTarget = DeformationConstants.getSmallULength(rowBody);

        if (zLL == null || zLL.length == 0) zLL = new double[largeTarget];
        if (zLR == null || zLR.length == 0) zLR = new double[largeTarget];
        if (zSL == null || zSL.length == 0) zSL = new double[smallTarget];
        if (zSR == null || zSR.length == 0) zSR = new double[smallTarget];
        if (zLB == null) zLB = new double[0];
        if (zSB == null) zSB = new double[0];

        // Step 2: Big U 横边闭合
        // 横边反演方向固定从右到左，起点拼接在右侧尾端
        // 公式：Z_LB[i] = Z_LB[i] - Z_LB[0] + Z_LR[end] + (Error / (length-1)) * i
        // 其中 Target_Diff = Z_LL[end] - Z_LR[end]
        //      Current_Diff = Z_LB[end] - Z_LB[0]
        //      Error = Target_Diff - Current_Diff
        double[] zLBClosed = closeBigUBottom(zLL, zLR, zLB);

        // Step 3: Small U fusion（主从基准融合）
        SmallUFusionService smallUFusionService = new SmallUFusionService(new BigUClosureService());
        SmallUFusionService.FusionResult fusionResult = smallUFusionService.fuse(zLL, zLR, zSL, zSR, zSB);
        double[] zSLFused = fusionResult.zSmallLeftFused;
        double[] zSRFused = fusionResult.zSmallRightFused;

        // Step 3.5: 小U横边新算法 = 基准线（左右尾端线性插值） + 加权微观细节
        double[] zSBClosed = buildSmallUBottom(zSLFused, zSRFused, zSB);

        // Step 4: Ghost nodes
        GhostNodeService ghostNodeService = new GhostNodeService();
        int nLarge = zLL.length;
        int nSmall = zSLFused.length;
        List<GhostNodeService.GhostNode> ghostNodes = ghostNodeService.buildGhostNodes(zLL, zLR, zLBClosed, nLarge);

        // Step 5: Build control points for TPS（对每条边降采样以控制总点数）
        List<double[]> controlPoints = new ArrayList<>();

        // 每条边最多取 80 个采样点（6条边 × 80 = 480，在500限制内）
        final int MAX_EDGE_SAMPLES = 80;

        // Large U Left track
        addSampledTrack(controlPoints, zLL, DeformationConstants.BIG_U_LEFT_Y, MAX_EDGE_SAMPLES);

        // Large U Right track
        addSampledTrack(controlPoints, zLR, DeformationConstants.BIG_U_RIGHT_Y, MAX_EDGE_SAMPLES);

        // Large U Bottom track - Y linearly distributed from BIG_U_RIGHT_Y to BIG_U_LEFT_Y
        if (zLBClosed.length > 0) {
            int step = Math.max(1, zLBClosed.length / MAX_EDGE_SAMPLES);
            for (int i = 0; i < zLBClosed.length; i += step) {
                double y = DeformationConstants.BIG_U_RIGHT_Y
                        - (double) i * (DeformationConstants.BIG_U_RIGHT_Y - DeformationConstants.BIG_U_LEFT_Y)
                        / Math.max(1, zLBClosed.length - 1);
                controlPoints.add(new double[]{(double) (nLarge - 1), y, zLBClosed[i]});
            }
        }

        // Small U Left track
        addSampledTrack(controlPoints, zSLFused, DeformationConstants.SMALL_U_LEFT_Y, MAX_EDGE_SAMPLES);

        // Small U Right track
        addSampledTrack(controlPoints, zSRFused, DeformationConstants.SMALL_U_RIGHT_Y, MAX_EDGE_SAMPLES);

        // Small U Bottom track - Y linearly distributed from SMALL_U_RIGHT_Y to SMALL_U_LEFT_Y
        if (zSBClosed.length > 0) {
            int step = Math.max(1, zSBClosed.length / MAX_EDGE_SAMPLES);
            for (int i = 0; i < zSBClosed.length; i += step) {
                double y = DeformationConstants.SMALL_U_RIGHT_Y
                        - (double) i * (DeformationConstants.SMALL_U_RIGHT_Y - DeformationConstants.SMALL_U_LEFT_Y)
                        / Math.max(1, zSBClosed.length - 1);
                controlPoints.add(new double[]{(double) (nSmall - 1), y, zSBClosed[i]});
            }
        }

        // Ghost nodes（只添加边界约束点，不添加全部尾部延伸点）
        // 前端约束（X=0 处）
        double zL0 = zLL.length > 0 ? zLL[0] : 0.0;
        double zR0 = zLR.length > 0 ? zLR[0] : 0.0;
        for (int y = 0; y <= 40; y += 5) {
            double ratio = y / 40.0;
            double z = zL0 * (1.0 - ratio) + zR0 * ratio;
            controlPoints.add(new double[]{0.0, (double) y, z});
        }
        // 左右边界约束（Y=0 和 Y=40）
        int edgeStep = Math.max(1, nLarge / 20);
        for (int i = 0; i < nLarge; i += edgeStep) {
            controlPoints.add(new double[]{(double) i, 0.0, zLL[i]});
            controlPoints.add(new double[]{(double) i, 40.0, zLR[i]});
        }

        System.out.println("TPS控制点数量: " + controlPoints.size());
        // Step 6: TPS interpolation
        ThinPlateSplineInterpolator tps = new ThinPlateSplineInterpolator();
        tps.buildModel(controlPoints);

        // 网格固定为 201×101：X=0~200（步长1m），Y=-5~45（步长0.5m）
        // X方向扩展到200统一长度，Y方向两侧各外扩5m用于全部展示模式的拼接平滑
        int gridWidth = 201;
        int gridHeight = 101;
        double xMinVal = 0.0;
        double xMaxVal = 200.0;
        double yMinVal = -5.0;
        double yMaxVal = 45.0;
        double[] gridX = new double[gridWidth];
        double[] gridY = new double[gridHeight];
        for (int i = 0; i < gridWidth; i++) gridX[i] = xMinVal + i * (xMaxVal - xMinVal) / (gridWidth - 1);
        for (int j = 0; j < gridHeight; j++) gridY[j] = yMinVal + j * (yMaxVal - yMinVal) / (gridHeight - 1);

        double[][] zGrid;
        if (tps.isSolved()) {
            zGrid = tps.interpolate(gridX, gridY);
            // Round to 2 decimal places
            for (int i = 0; i < gridWidth; i++)
                for (int j = 0; j < gridHeight; j++)
                    zGrid[i][j] = Math.round(zGrid[i][j] * 100.0) / 100.0;
        } else {
            System.err.println("TPS求解失败！控制点数量=" + controlPoints.size() + ", 排体=" + rowBody);
            zGrid = new double[gridWidth][gridHeight]; // all zeros
        }

        // Step 7: Build SurfaceMeshDTO
        SurfaceMeshDTO dto = new SurfaceMeshDTO();
        dto.setRowBody(rowBody);
        dto.setGridWidth(gridWidth);
        dto.setGridHeight(gridHeight);
        dto.setXMin(xMinVal);
        dto.setXMax(xMaxVal);
        dto.setYMin(yMinVal);
        dto.setYMax(yMaxVal);
        dto.setZGrid(zGrid);

        // Step 8: Build 6 cable tracks
        dto.setLargeULeft(buildTrack(zLL, DeformationConstants.BIG_U_LEFT_Y, 0));
        dto.setLargeURight(buildTrack(zLR, DeformationConstants.BIG_U_RIGHT_Y, 0));

        // Large U Bottom track with Y linearly from BIG_U_LEFT_Y to BIG_U_RIGHT_Y
        List<double[]> lbTrack = new ArrayList<>();
        for (int i = 0; i < zLBClosed.length; i++) {
            double y = DeformationConstants.BIG_U_RIGHT_Y
                    - i * (DeformationConstants.BIG_U_RIGHT_Y - DeformationConstants.BIG_U_LEFT_Y)
                    / Math.max(1, zLBClosed.length - 1);
            double x = (double) (nLarge - 1);
            y = Math.round(y * 100.0) / 100.0;
            double z = Math.round(zLBClosed[i] * 100.0) / 100.0;
            lbTrack.add(new double[]{x, y, z});
        }
        dto.setLargeUBottom(lbTrack);

        dto.setSmallULeft(buildTrack(zSLFused, DeformationConstants.SMALL_U_LEFT_Y, 0));
        dto.setSmallURight(buildTrack(zSRFused, DeformationConstants.SMALL_U_RIGHT_Y, 0));

        // Small U Bottom track with Y linearly from SMALL_U_LEFT_Y to SMALL_U_RIGHT_Y
        List<double[]> sbTrack = new ArrayList<>();
        for (int i = 0; i < zSBClosed.length; i++) {
            double y = DeformationConstants.SMALL_U_RIGHT_Y
                    - i * (DeformationConstants.SMALL_U_RIGHT_Y - DeformationConstants.SMALL_U_LEFT_Y)
                    / Math.max(1, zSBClosed.length - 1);
            double x = (double) (nSmall - 1);
            y = Math.round(y * 100.0) / 100.0;
            double z = Math.round(zSBClosed[i] * 100.0) / 100.0;
            sbTrack.add(new double[]{x, y, z});
        }
        dto.setSmallUBottom(sbTrack);

        // Step 9: Set hasBrokenCable
        dto.setHasBrokenCable("25".equals(rowBody));

        // Step 10: Set ghostCableIndices（25号右侧前84个点为补偿段）
        if ("25".equals(rowBody)) {
            int paddingCount = DeformationConstants.LARGE_LEN_195 - DeformationConstants.P25_RIGHT_COUNT; // 84
            List<Integer> ghostIndices = new ArrayList<>();
            for (int i = 0; i < paddingCount; i++) {
                ghostIndices.add(i);
            }
            dto.setGhostCableIndices(ghostIndices);
        } else {
            dto.setGhostCableIndices(new ArrayList<>());
        }

        // Step 11: 单位转换 mm → m
        convertZUnitMmToM(dto);

        return dto;
    }

    /**
     * 将 SurfaceMeshDTO 中所有 Z 值的单位从 mm 转换为 m（除以1000）
     */
    private void convertZUnitMmToM(SurfaceMeshDTO dto) {
        double[][] zGrid = dto.getZGrid();
        if (zGrid != null) {
            for (int i = 0; i < zGrid.length; i++) {
                if (zGrid[i] != null) {
                    for (int j = 0; j < zGrid[i].length; j++) {
                        zGrid[i][j] = round(zGrid[i][j] / 1000.0);
                    }
                }
            }
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

    /** 消除浮点精度：四舍五入到合理小数位（z值4位≈0.1mm，坐标2位≈1cm） */
    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }


    /**
     * 构建光缆轨迹点列表
     *
     * @param zValues 形变Z值数组
     * @param fixedY  固定的Y坐标
     * @param startX  起始X坐标
     * @return 轨迹点列表 [[x, y, z], ...]，Z值保留2位小数
     */
    private List<double[]> buildTrack(double[] zValues, double fixedY, int startX) {
        List<double[]> track = new ArrayList<>();
        for (int i = 0; i < zValues.length; i++) {
            double z = Math.round(zValues[i] * 100.0) / 100.0;
            track.add(new double[]{startX + i, fixedY, z});
        }
        return track;
    }

    /**
     * 对一条边的Z值数组进行降采样后添加到TPS控制点列表
     * X坐标从0开始，步长1；Y坐标固定
     *
     * @param controlPoints 控制点列表（会被修改）
     * @param zValues       边的Z值数组
     * @param fixedY        固定的Y坐标
     * @param maxSamples    最大采样点数
     */
    private void addSampledTrack(List<double[]> controlPoints, double[] zValues, double fixedY, int maxSamples) {
        if (zValues == null || zValues.length == 0) return;
        int step = Math.max(1, zValues.length / maxSamples);
        for (int i = 0; i < zValues.length; i += step) {
            controlPoints.add(new double[]{(double) i, fixedY, zValues[i]});
        }
        // 确保最后一个点也被包含
        int lastIdx = zValues.length - 1;
        if (lastIdx % step != 0) {
            controlPoints.add(new double[]{(double) lastIdx, fixedY, zValues[lastIdx]});
        }
    }
}
