package service.impl;

import enumObject.OptimizeTargetEnum;
import enumObject.TransportModeEnum;
import model.*;
import service.HeuristicCalculator;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 运输路径规划器的核心实现类。
 * 负责根据用户查询（起点、终点、是否需要同城提送货等），
 * 结合运输网络和费用规则，计算出最优的运输方案。
 */
public class TransportGraphRouter {

    // 存储整个运输网络的图结构，key是出发地-目的地对，value是对应的运输方式列表
    private final Map<CityPair, List<TransportEdge>> transportGraph;
    // 优化后的出边查找Map，key为出发城市，value为该城市所有的出边列表
    private final Map<String, List<TransportEdge>> outgoingEdgesMap;
    // 城市基础信息Map，可用于获取城市坐标等信息
    private final Map<String, CityBaseData> cityBaseDataMap;
    // 启发式计算器，用于A*算法中估算节点到终点的成本
    private final HeuristicCalculator heuristicCalculator;

    // 路径最大分段数限制
    private int maxSegments = 5;
    // 代驾最大运输距离限制
    private int maxDriverDistance = 1000;
    // 同城提/送货服务基础价格
    private BigDecimal sameCityServicePrice = new BigDecimal("200");
    // 同城提/送货服务基础时间（小时）
    private int sameCityServiceTime = 2;

    /**
     * 构造函数，初始化路由计算器
     * @param transportGraph 运输网络图
     * @param cityBaseDataMap 城市基础信息
     * @param heuristicCalculator 启发式计算器
     */
    public TransportGraphRouter(Map<CityPair, List<TransportEdge>> transportGraph,
                                Map<String, CityBaseData> cityBaseDataMap,
                                HeuristicCalculator heuristicCalculator) {
        this.transportGraph = transportGraph;
        this.cityBaseDataMap = cityBaseDataMap;
        this.heuristicCalculator = heuristicCalculator;

        // 在构造函数中，从原始 transportGraph 构建 outgoingEdgesMap
        this.outgoingEdgesMap = buildOutgoingEdgesMap(transportGraph);
    }

    /**
     * 从原始的 transportGraph 构建一个优化的出边查找Map。
     * 这个方法只在对象初始化时执行一次。
     */
    private Map<String, List<TransportEdge>> buildOutgoingEdgesMap(Map<CityPair, List<TransportEdge>> transportGraph) {
        Map<String, List<TransportEdge>> resultMap = new HashMap<>();

        for (Map.Entry<CityPair, List<TransportEdge>> entry : transportGraph.entrySet()) {
            CityPair cityPair = entry.getKey();
            String fromCity = cityPair.getFromCity();
            List<TransportEdge> edges = entry.getValue();

            // 如果 resultMap 中还没有这个出发城市的条目，就创建一个新的空列表
            resultMap.computeIfAbsent(fromCity, k -> new ArrayList<>());

            // 将当前城市对的所有边添加到对应的列表中
            resultMap.get(fromCity).addAll(edges);
        }

        return resultMap;
    }

    /**
     * 【核心入口方法】执行路由规划
     * @param query 用户查询条件
     * @return 包含所有符合条件的方案的列表
     */
    public List<RoutePlan> planRoute(RouteQuery query) {
        List<RoutePlan> finalResults = new ArrayList<>();

        // 1. --- 寻找“大板车”方案中的最优解 ---
        // 分别计算时间最优和价格最优的大板车干线运输路径
        RoutePlan timeOptimalBigTruckPlan = findBestRoute(query, OptimizeTargetEnum.TIME);
        RoutePlan priceOptimalBigTruckPlan = findBestRoute(query, OptimizeTargetEnum.PRICE);

        // 为找到的干线运输路径添加同城提/送货服务，并应用费用规则
        if (timeOptimalBigTruckPlan != null) {
            RoutePlan processedTimePlan = addCityServicesToPlan(timeOptimalBigTruckPlan, query);
            if(processedTimePlan != null) finalResults.add(processedTimePlan);
        }
        if (priceOptimalBigTruckPlan != null) {
            // 避免重复添加（如果时间最优和价格最优是同一个方案）
            if (timeOptimalBigTruckPlan == null || !priceOptimalBigTruckPlan.getTotalPrice().equals(timeOptimalBigTruckPlan.getTotalPrice()) ||
                    priceOptimalBigTruckPlan.getTotalTime() != timeOptimalBigTruckPlan.getTotalTime()) {
                RoutePlan processedPricePlan = addCityServicesToPlan(priceOptimalBigTruckPlan, query);
                if(processedPricePlan != null) finalResults.add(processedPricePlan);
            }
        }

        // 2. --- 添加其他单一运输方式的直达方案 ---
        // 例如：海运直达、铁路直达、代驾直达等
        List<TransportModeEnum> otherModes = Arrays.asList(
                TransportModeEnum.SHIP,
                TransportModeEnum.RAIL,
                TransportModeEnum.DRIVER,
                TransportModeEnum.SMALL_TRUCK
        );

        for (TransportModeEnum mode : otherModes) {
            RoutePlan directPlan = findDirectRoute(query, mode);
            if (directPlan != null) {
                finalResults.add(directPlan);
            }
        }

        return finalResults;
    }

    /**
     * 【核心算法】使用A*算法寻找最优的干线运输路径
     * @param query 查询条件
     * @param optimizeTarget 优化目标 (时间/价格)
     * @return 最优干线路径方案，如果未找到则返回null
     */
    private RoutePlan findBestRoute(RouteQuery query, OptimizeTargetEnum optimizeTarget) {
        String startCity = query.getFromCity();
        String endCity = query.getToCity();

        // A*算法的优先队列（open set），按fScore排序
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::getFScore));
        // A*算法的已访问节点集合（closed set），记录已经处理过的节点
        Map<AStarNodeKey, AStarNode> closedSet = new HashMap<>();

        // 创建起始节点
        AStarNode startNode = new AStarNode();
        startNode.setCity(startCity);
        startNode.setSegmentCount(0);
        startNode.setPreviousMode(null);
        startNode.setCurrentMode(null);
        startNode.setGScore(0); // 从起点到当前节点的实际成本
        startNode.setHScore(heuristicCalculator.calculate(startCity, endCity, optimizeTarget)); // 预估成本
        startNode.setPrevNode(null);
        startNode.setEdge(null);

        openSet.add(startNode);

        while (!openSet.isEmpty()) {
            // 取出fScore最小的节点
            AStarNode currentNode = openSet.poll();
            AStarNodeKey currentKey = new AStarNodeKey(
                    currentNode.getCity(),
                    currentNode.getSegmentCount(),
                    currentNode.getPreviousMode(),
                    currentNode.getCurrentMode()
            );

            // 找到终点，立即返回重建的路径
            if (currentNode.getCity().equals(endCity)) {
                return reconstructPlan(currentNode, query, optimizeTarget);
            }

            // 如果该节点已被处理过且记录的gScore更小，则跳过
            if (closedSet.containsKey(currentKey) && closedSet.get(currentKey).getGScore() <= currentNode.getGScore()) {
                continue;
            }

            // 将当前节点加入已处理集合
            closedSet.put(currentKey, currentNode);

            // 获取当前节点的所有出边（可到达的下一个节点）
            List<TransportEdge> outgoingEdges = getOutgoingEdges(currentNode, true);

            for (TransportEdge edge : outgoingEdges) {
                // 检查转移是否合法（例如：代驾之后只能接大板车）
                if (!canTransition(currentNode, edge)) {
                    continue;
                }

                // 检查路径分段数是否超限
                int newSegmentCount = currentNode.getSegmentCount() + 1;
                if (newSegmentCount > maxSegments) {
                    continue;
                }

                // 检查代驾距离是否超限
                if (edge.getMode() == TransportModeEnum.DRIVER && edge.getMileage() > maxDriverDistance) {
                    continue;
                }

                // 计算新的gScore（从起点到下一个节点的实际成本，包含预估的同城费）
                double newGScore = calculateNewGScore(currentNode, edge, optimizeTarget, query, endCity);

                // 创建邻居节点
                AStarNode neighborNode = new AStarNode();
                neighborNode.setCity(edge.getToCity());
                neighborNode.setSegmentCount(newSegmentCount);
                neighborNode.setPreviousMode(currentNode.getCurrentMode());
                neighborNode.setCurrentMode(edge.getMode());
                neighborNode.setGScore(newGScore);
                neighborNode.setHScore(heuristicCalculator.calculate(edge.getToCity(), endCity, optimizeTarget));
                neighborNode.setPrevNode(currentNode);
                neighborNode.setEdge(edge);

                AStarNodeKey neighborKey = new AStarNodeKey(
                        neighborNode.getCity(),
                        neighborNode.getSegmentCount(),
                        neighborNode.getPreviousMode(),
                        neighborNode.getCurrentMode()
                );

                // 如果邻居节点不在openSet中，或者有更优的路径，则加入/更新openSet
                if (!closedSet.containsKey(neighborKey) || newGScore < closedSet.get(neighborKey).getGScore()) {
                    openSet.add(neighborNode);
                }
            }
        }
        return null; // 未找到路径
    }

    /**
     * 查找单一运输方式的直达路线
     * @param query 查询条件
     * @param mode 运输方式
     * @return 直达路线方案，如果不存在则返回null
     */
    private RoutePlan findDirectRoute(RouteQuery query, TransportModeEnum mode) {
        String fromCity = query.getFromCity();
        String toCity = query.getToCity();

        // 1. 检查是否存在从 fromCity 到 toCity 的直达边
        CityPair directPair = new CityPair(fromCity, toCity);
        if (!transportGraph.containsKey(directPair)) {
            return null;
        }

        List<TransportEdge> directEdges = transportGraph.get(directPair);
        TransportEdge chosenEdge = directEdges.stream()
                .filter(e -> e.getMode() == mode)
                .findFirst()
                .orElse(null);

        if (chosenEdge == null) {
            return null;
        }

        // 代驾直达需要检查距离限制
        if (mode == TransportModeEnum.DRIVER && chosenEdge.getMileage() > maxDriverDistance) {
            return null;
        }

        // 2. 构建分段详情
        List<RouteSegment> segments = new ArrayList<>();
        BigDecimal totalPrice = chosenEdge.getPrice();
        int totalTime = chosenEdge.getTimeHours();

        int segmentNo = 1;
        // 处理同城提车
        if (query.isNeedSameCityPick()) {
            RouteSegment pickupSegment = createSameCitySegment(segmentNo++, fromCity, TransportModeEnum.DRIVER);
            // 代驾直达时，提车免费
            if (mode != TransportModeEnum.DRIVER) {
                pickupSegment.setPrice(sameCityServicePrice);
                totalPrice = totalPrice.add(sameCityServicePrice);
            }
            segments.add(pickupSegment);
            totalTime += sameCityServiceTime;
        }

        // 主要运输段
        RouteSegment mainSegment = new RouteSegment();
        mainSegment.setSegmentNo(segmentNo++);
        mainSegment.setFromCity(fromCity);
        mainSegment.setToCity(toCity);
        mainSegment.setMode(mode);
        mainSegment.setMileage(chosenEdge.getMileage());
        mainSegment.setTimeHours(chosenEdge.getTimeHours());
        mainSegment.setPrice(chosenEdge.getPrice());
        mainSegment.setLastMile(false);
        segments.add(mainSegment);

        // 处理同城送车
        if (query.isNeedSameCityDeliver()) {
            RouteSegment deliverySegment = createSameCitySegment(segmentNo++, toCity, TransportModeEnum.DRIVER);
            // 代驾直达时，送车免费
            if (mode != TransportModeEnum.DRIVER) {
                deliverySegment.setPrice(sameCityServicePrice);
                totalPrice = totalPrice.add(sameCityServicePrice);
            }
            segments.add(deliverySegment);
            totalTime += sameCityServiceTime;
        }

        // 3. 构建并返回 RoutePlan
        RoutePlan plan = new RoutePlan();
        plan.setFromCity(fromCity);
        plan.setToCity(toCity);
        plan.setSegments(segments);
        plan.setSegmentCount(segments.size());
        plan.setModeList(segments.stream().map(RouteSegment::getMode).collect(Collectors.toList()));
        plan.setTotalPrice(totalPrice);
        plan.setTotalTime(totalTime);
        plan.setNeedSameCityPick(query.isNeedSameCityPick());
        plan.setNeedSameCityDeliver(query.isNeedSameCityDeliver());
        plan.setOptimizeTarget(null);

        return plan;
    }

    /**
     * 为一个已有的干线运输路径方案添加同城提车和送车服务段。
     * 干线成本 + 同城服务成本 已在 findBestRoute 阶段计算为最终 totalPrice。
     * 此方法主要负责构建最终的 RouteSegment 列表。
     */
    private RoutePlan addCityServicesToPlan(RoutePlan originalPlan, RouteQuery query) {
        // 校验原始路径是否符合支线规则（例如：代驾只能在路径首尾）
        if (!isRouteValid(originalPlan)) {
            System.err.println("警告：找到的路径不符合支线规则，已跳过。");
            return null;
        }

        RoutePlan newPlan = new RoutePlan();
        newPlan.setFromCity(originalPlan.getFromCity());
        newPlan.setToCity(originalPlan.getToCity());
        newPlan.setNeedSameCityPick(originalPlan.isNeedSameCityPick());
        newPlan.setNeedSameCityDeliver(originalPlan.isNeedSameCityDeliver());
        newPlan.setOptimizeTarget(originalPlan.getOptimizeTarget());

        List<RouteSegment> originalSegments = originalPlan.getSegments();
        List<RouteSegment> newSegments = new ArrayList<>();

        int segmentNo = 1;
        int totalTime = originalPlan.getTotalTime();

        // 1. 添加同城提车服务
        if (query.isNeedSameCityPick()) {
            RouteSegment pickupSegment = createSameCitySegment(segmentNo++, query.getFromCity(), TransportModeEnum.DRIVER);
            boolean isFree = originalPlan.getFirstMainMode() == TransportModeEnum.DRIVER;
            if (isFree) {
                pickupSegment.setPrice(BigDecimal.ZERO);
            } else {
                pickupSegment.setPrice(sameCityServicePrice);
            }
            newSegments.add(pickupSegment);
            totalTime += sameCityServiceTime;
        }

        // 2. 添加原始的干线运输段，并更新序号
        for (RouteSegment seg : originalSegments) {
            RouteSegment newSeg = new RouteSegment();
            newSeg.setSegmentNo(segmentNo++);
            newSeg.setFromCity(seg.getFromCity());
            newSeg.setToCity(seg.getToCity());
            newSeg.setMode(seg.getMode());
            newSeg.setMileage(seg.getMileage());
            newSeg.setTimeHours(seg.getTimeHours());
            newSeg.setPrice(seg.getPrice());
            newSeg.setLastMile(false);
            newSegments.add(newSeg);
        }

        // 3. 添加同城送车服务
        if (query.isNeedSameCityDeliver()) {
            RouteSegment deliverySegment = createSameCitySegment(segmentNo++, query.getToCity(), TransportModeEnum.DRIVER);
            boolean isFree = originalPlan.getLastMainMode() == TransportModeEnum.DRIVER;
            if (isFree) {
                deliverySegment.setPrice(BigDecimal.ZERO);
            } else {
                deliverySegment.setPrice(sameCityServicePrice);
            }
            newSegments.add(deliverySegment);
            totalTime += sameCityServiceTime;
        }

        // 4. 设置新 Plan 的属性
        newPlan.setSegments(newSegments);
        newPlan.setSegmentCount(newSegments.size());
        newPlan.setTotalPrice(originalPlan.getTotalPrice()); // 使用已计算好的最终价格
        newPlan.setTotalTime(totalTime);
        newPlan.setModeList(newSegments.stream().map(RouteSegment::getMode).collect(Collectors.toList()));

        return newPlan;
    }

    /**
     * 创建一个同城服务的段 (提车或送车)
     */
    private RouteSegment createSameCitySegment(int segmentNo, String city, TransportModeEnum mode) {
        RouteSegment segment = new RouteSegment();
        segment.setSegmentNo(segmentNo);
        segment.setFromCity(city);
        segment.setToCity(city);
        segment.setMode(mode);
        segment.setMileage(0);
        segment.setTimeHours(sameCityServiceTime);
        segment.setPrice(BigDecimal.ZERO); // 初始价格设为0，后续根据规则调整
        segment.setLastMile(true);
        return segment;
    }

    /**
     * 校验一个路径是否符合“支线(DRIVER)只能在首尾”的规则
     */
    private boolean isRouteValid(RoutePlan plan) {
        List<RouteSegment> segments = plan.getSegments();
        if (segments.isEmpty()) return true;

        boolean hasSeenNonDriver = false;

        for (RouteSegment seg : segments) {
            if (seg.getMode() == TransportModeEnum.DRIVER) {
                if (hasSeenNonDriver) {
                    return false;
                }
            } else {
                hasSeenNonDriver = true;
            }
        }
        return true;
    }

    /**
     * 获取当前节点所有可能的出边
     * @param currentNode 当前节点
     * @param bigTruckOnly 是否只考虑大板车运输
     * @return 出边列表
     */
    private List<TransportEdge> getOutgoingEdges(AStarNode currentNode, boolean bigTruckOnly) {
        String currentCity = currentNode.getCity();

        // 1. 从优化后的 Map 中直接获取当前城市的所有出边
        // 如果没有出边，返回一个空列表，避免空指针异常
        List<TransportEdge> allOutgoingEdges = outgoingEdgesMap.getOrDefault(currentCity, Collections.emptyList());

        // 2. 如果不需要过滤，则直接返回所有出边
        if (!bigTruckOnly) {
            return allOutgoingEdges;
        }
        // 3. 如果需要过滤（只保留 BIG_TRUCK 和 DRIVER），则进行流式过滤
        return allOutgoingEdges.stream()
                .filter(e -> e.getMode() == TransportModeEnum.BIG_TRUCK || e.getMode() == TransportModeEnum.DRIVER)
                .collect(Collectors.toList());
    }

    /**
     * 【核心转移规则】判断从当前节点到下一个节点的转移是否合法
     */
    private boolean canTransition(AStarNode currentNode, TransportEdge nextEdge) {
        TransportModeEnum currentMode = currentNode.getCurrentMode();
        TransportModeEnum nextMode = nextEdge.getMode();

        // 1. 如果当前模式是 DRIVER (支线)，下一段必须是 BIG_TRUCK (干线)
        if (currentMode == TransportModeEnum.DRIVER) {
            return nextMode == TransportModeEnum.BIG_TRUCK;
        }
        // 2. 如果当前模式是 BIG_TRUCK (干线)，下一段可以是 BIG_TRUCK (继续干线) 或 DRIVER (末端支线)
        if (currentMode == TransportModeEnum.BIG_TRUCK) {
            return nextMode == TransportModeEnum.BIG_TRUCK || nextMode == TransportModeEnum.DRIVER;
        }
        // 3. 初始状态，可以选择 BIG_TRUCK (直接干线) 或 DRIVER (起始支线)
        if (currentMode == null) {
            return nextMode == TransportModeEnum.BIG_TRUCK || nextMode == TransportModeEnum.DRIVER;
        }
        // 4. 其他模式不允许
        return false;
    }

    /**
     * 根据优化目标计算新的G值 (从起点到当前邻居节点的累计实际成本)
     * 当优化目标是价格时，会预估并加入可能产生的同城提/送货费用。
     */
    private double calculateNewGScore(AStarNode currentNode, TransportEdge edge, OptimizeTargetEnum target, RouteQuery query, String endCity) {
        double newGScore = currentNode.getGScore();

        if (target == OptimizeTargetEnum.TIME) {
            newGScore += edge.getTimeHours();
        } else { // PRICE
            newGScore += edge.getPrice().doubleValue();

            // 1. 预估同城提车成本
            // 如果当前节点是起点，并且需要提车服务
            if (currentNode.getPrevNode() == null && query.isNeedSameCityPick()) {
                // 判断未来第一条路由是否是代驾。如果是，提车费将被减免。
                if (edge.getMode() != TransportModeEnum.DRIVER) {
                    newGScore += sameCityServicePrice.doubleValue();
                }
            }

            // 2. 预估同城送车成本
            // 如果下一个节点(neighbor)是终点，并且需要送车服务
            if (edge.getToCity().equals(endCity) && query.isNeedSameCityDeliver()) {
                // 判断未来最后一条路由是否是代驾。如果是，送车费将被减免。
                if (edge.getMode() != TransportModeEnum.DRIVER) {
                    newGScore += sameCityServicePrice.doubleValue();
                }
            }
        }

        return newGScore;
    }

    /**
     * 从目标节点回溯，重建完整的路径规划方案
     */
    private RoutePlan reconstructPlan(AStarNode endNode, RouteQuery query, OptimizeTargetEnum optimizeTarget) {
        List<TransportEdge> edges = new ArrayList<>();
        AStarNode current = endNode;
        while (current.getPrevNode() != null) {
            edges.add(current.getEdge());
            current = current.getPrevNode();
        }
        Collections.reverse(edges);

        List<RouteSegment> segments = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalTime = 0;
        for (int i = 0; i < edges.size(); i++) {
            TransportEdge edge = edges.get(i);
            RouteSegment segment = new RouteSegment();
            segment.setSegmentNo(i + 1);
            segment.setFromCity(edge.getFromCity());
            segment.setToCity(edge.getToCity());
            segment.setMode(edge.getMode());
            segment.setMileage(edge.getMileage());
            segment.setTimeHours(edge.getTimeHours());
            segment.setPrice(edge.getPrice());
            segment.setLastMile(false);

            segments.add(segment);
            totalPrice = totalPrice.add(edge.getPrice());
            totalTime += edge.getTimeHours();
        }

        RoutePlan plan = new RoutePlan();
        plan.setFromCity(query.getFromCity());
        plan.setToCity(query.getToCity());
        plan.setSegments(segments);
        plan.setSegmentCount(segments.size());
        plan.setModeList(segments.stream().map(RouteSegment::getMode).collect(Collectors.toList()));
        plan.setTotalTime(totalTime);
        plan.setNeedSameCityPick(query.isNeedSameCityPick());
        plan.setNeedSameCityDeliver(query.isNeedSameCityDeliver());
        plan.setOptimizeTarget(optimizeTarget);

        // 记录干线的首尾模式
        if (!segments.isEmpty()) {
            plan.setFirstMainMode(segments.get(0).getMode());
            plan.setLastMainMode(segments.get(segments.size() - 1).getMode());
        }

        // 设置最终价格（已包含预估的同城费）
        // 注意：这里的 endNode.getGScore() 已经是包含了干线费和预估同城费的总成本
        plan.setTotalPrice(BigDecimal.valueOf(endNode.getGScore()));

        return plan;
    }

    // --- Getters and Setters ---
    public int getMaxSegments() { return maxSegments; }
    public void setMaxSegments(int maxSegments) { this.maxSegments = maxSegments; }
    public int getMaxDriverDistance() { return maxDriverDistance; }
    public void setMaxDriverDistance(int maxDriverDistance) { this.maxDriverDistance = maxDriverDistance; }
    public BigDecimal getSameCityServicePrice() { return sameCityServicePrice; }
    public void setSameCityServicePrice(BigDecimal sameCityServicePrice) { this.sameCityServicePrice = sameCityServicePrice; }
    public int getSameCityServiceTime() { return sameCityServiceTime; }
    public void setSameCityServiceTime(int sameCityServiceTime) { this.sameCityServiceTime = sameCityServiceTime; }
}