package com.example.smartrouter;

import enumObject.OptimizeTargetEnum;
import enumObject.TransportModeEnum;
import model.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import service.HeuristicCalculator;
import service.impl.DefaultHeuristicCalculator;
import service.impl.TransportGraphRouter;

import java.math.BigDecimal;
import java.util.*;

@SpringBootApplication
public class SmartRouterApplication {

    public static void main(String[] args) {
        System.out.println("=== 智慧物流路由规划系统 ===");

        // 1. 准备城市基础数据 (增加更多城市)
        Map<String, CityBaseData> cityBaseDataMap = new HashMap<>();
        // 华北
        cityBaseDataMap.put("北京", new CityBaseData("北京", "北京", 39.9042, 116.4074, 39.9042, 116.4074, 0));
        cityBaseDataMap.put("天津", new CityBaseData("天津", "天津", 39.9042, 117.2000, 39.9042, 117.2000, 0));
        cityBaseDataMap.put("沈阳", new CityBaseData("沈阳", "沈阳", 41.8000, 123.4333, 41.8000, 123.4333, 0));
        // 华东
        cityBaseDataMap.put("上海", new CityBaseData("上海", "上海", 31.2304, 121.4737, 31.2304, 121.4737, 0));
        cityBaseDataMap.put("南京", new CityBaseData("南京", "南京", 32.0472, 118.7969, 32.0472, 118.7969, 0));
        cityBaseDataMap.put("杭州", new CityBaseData("杭州", "杭州", 30.2791, 120.1551, 30.2791, 120.1551, 0));
        cityBaseDataMap.put("济南", new CityBaseData("济南", "济南", 36.6750, 117.0000, 36.6750, 117.0000, 0));
        // 华中
        cityBaseDataMap.put("武汉", new CityBaseData("武汉", "武汉", 30.5928, 114.3055, 30.5928, 114.3055, 0));
        cityBaseDataMap.put("郑州", new CityBaseData("郑州", "郑州", 34.7500, 113.7500, 34.7500, 113.7500, 0));
        cityBaseDataMap.put("长沙", new CityBaseData("长沙", "长沙", 28.2000, 113.0000, 28.2000, 113.0000, 0));
        // 华南
        cityBaseDataMap.put("广州", new CityBaseData("广州", "广州", 23.1291, 113.2644, 23.1291, 113.2644, 0));
        cityBaseDataMap.put("深圳", new CityBaseData("深圳", "深圳", 22.5431, 114.0579, 22.5431, 114.0579, 0));
        cityBaseDataMap.put("南宁", new CityBaseData("南宁", "南宁", 22.8167, 108.3167, 22.8167, 108.3167, 0));
        // 西南
        cityBaseDataMap.put("成都", new CityBaseData("成都", "成都", 30.5728, 104.0668, 30.5728, 104.0668, 0));
        cityBaseDataMap.put("重庆", new CityBaseData("重庆", "重庆", 29.5667, 106.5000, 29.5667, 106.5000, 0));
        cityBaseDataMap.put("昆明", new CityBaseData("昆明", "昆明", 25.0333, 102.7000, 25.0333, 102.7000, 0));
        // 西北
        cityBaseDataMap.put("西安", new CityBaseData("西安", "西安", 34.3416, 108.9398, 34.3416, 108.9398, 0));
        cityBaseDataMap.put("兰州", new CityBaseData("兰州", "兰州", 36.0500, 103.8000, 36.0500, 103.8000, 0));

        // 2. 准备运输图数据（构建一个更复杂、更密集的网络以支持多段最优路径）
        Map<CityPair, List<TransportEdge>> transportGraph = new HashMap<>();

        // --- 策略：提高直达成本，降低中转成本，引导算法寻找多段路径 ---

        // 北京出发的长途直达线路 (价格较高)
        addEdge(transportGraph, "北京", "广州", TransportModeEnum.BIG_TRUCK, 2100, 28, new BigDecimal("12000")); // 提高价格
        addEdge(transportGraph, "北京", "武汉", TransportModeEnum.BIG_TRUCK, 1200, 20, new BigDecimal("7000"));   // 提高价格
        addEdge(transportGraph, "北京", "上海", TransportModeEnum.BIG_TRUCK, 1300, 22, new BigDecimal("7500"));   // 提高价格

        // 北京周边及华北地区 (价格低廉，鼓励中转)
        addEdge(transportGraph, "北京", "天津", TransportModeEnum.BIG_TRUCK, 120, 3, new BigDecimal("500"));
        addEdge(transportGraph, "北京", "沈阳", TransportModeEnum.BIG_TRUCK, 700, 12, new BigDecimal("3000"));
        addEdge(transportGraph, "北京", "济南", TransportModeEnum.BIG_TRUCK, 490, 10, new BigDecimal("2200"));
        addEdge(transportGraph, "天津", "济南", TransportModeEnum.BIG_TRUCK, 370, 8, new BigDecimal("1600"));
        addEdge(transportGraph, "北京", "济南", TransportModeEnum.DRIVER, 670, 8, new BigDecimal("1500"));

        addEdge(transportGraph, "沈阳", "北京", TransportModeEnum.BIG_TRUCK, 700, 12, new BigDecimal("3000"));

        // 华东地区 (价格低廉，鼓励中转)
        addEdge(transportGraph, "济南", "郑州", TransportModeEnum.BIG_TRUCK, 450, 9, new BigDecimal("2000"));
        addEdge(transportGraph, "郑州", "武汉", TransportModeEnum.BIG_TRUCK, 530, 11, new BigDecimal("2400"));
        addEdge(transportGraph, "武汉", "长沙", TransportModeEnum.BIG_TRUCK, 350, 7, new BigDecimal("1500"));
        addEdge(transportGraph, "长沙", "广州", TransportModeEnum.BIG_TRUCK, 700, 14, new BigDecimal("3100")); // 关键的最后一段

        // 为了凑够5段，设计一条更优的路径: 北京 -> 天津 -> 济南 -> 郑州 -> 武汉 -> 长沙 -> 广州 (6段)
        // 为了让5段成为最优，我们可以让其中某段的成本非常低，或者提供另一条5段的路径。
        // 这里我们让 "北京 -> 济南 -> 郑州 -> 武汉 -> 长沙 -> 广州" (5段干线) 成为价格最优。
        // 上面的边已经定义好了，它们的总和是 2200 + 2000 + 2400 + 1500 + 3100 = 11200
        // 这比直达的 12000 便宜。

        // 再提供一条时间最优但中转次数少的路径作为对比
        addEdge(transportGraph, "北京", "郑州", TransportModeEnum.BIG_TRUCK, 690, 15, new BigDecimal("9000")); // 时间比两段快，但价格高
        addEdge(transportGraph, "郑州", "长沙", TransportModeEnum.BIG_TRUCK, 880, 19, new BigDecimal("4000"));
        addEdge(transportGraph, "长沙", "广州", TransportModeEnum.BIG_TRUCK, 700, 14, new BigDecimal("3100"));
        // 北京 -> 郑州 -> 长沙 -> 广州 (3段干线) 时间 15+19+14=48, 价格 9000+4000+3100=16100

        // 其他地区线路，丰富网络
        addEdge(transportGraph, "上海", "南京", TransportModeEnum.BIG_TRUCK, 300, 6, new BigDecimal("1300"));
        addEdge(transportGraph, "南京", "武汉", TransportModeEnum.BIG_TRUCK, 550, 12, new BigDecimal("2500"));
        addEdge(transportGraph, "武汉", "重庆", TransportModeEnum.BIG_TRUCK, 850, 18, new BigDecimal("3800"));
        addEdge(transportGraph, "重庆", "成都", TransportModeEnum.BIG_TRUCK, 300, 7, new BigDecimal("1300"));
        addEdge(transportGraph, "成都", "昆明", TransportModeEnum.BIG_TRUCK, 1100, 23, new BigDecimal("4900"));
        addEdge(transportGraph, "西安", "兰州", TransportModeEnum.BIG_TRUCK, 650, 14, new BigDecimal("2900"));
        addEdge(transportGraph, "西安", "郑州", TransportModeEnum.BIG_TRUCK, 510, 11, new BigDecimal("2300"));

        // 保留一些其他运输方式
        addEdge(transportGraph, "北京", "广州", TransportModeEnum.SHIP, 1200, 20, new BigDecimal("6000"));
        addEdge(transportGraph, "北京", "广州", TransportModeEnum.DRIVER, 1900, 24, new BigDecimal("9600"));
        addEdge(transportGraph, "上海", "广州", TransportModeEnum.SHIP, 1800, 72, new BigDecimal("2500"));
        addEdge(transportGraph, "广州", "深圳", TransportModeEnum.DRIVER, 150, 2, new BigDecimal("1300"));

        // 3. 创建路由计算器
        HeuristicCalculator heuristic = new DefaultHeuristicCalculator(cityBaseDataMap);
        TransportGraphRouter router = new TransportGraphRouter(transportGraph, cityBaseDataMap, heuristic);

        // 配置参数
        router.setMaxSegments(5); // 增加最大分段数，确保5段路径不会被过滤
        router.setMaxDriverDistance(1500);
        router.setSameCityServicePrice(new BigDecimal("200"));
        router.setSameCityServiceTime(2);

        // 4. 创建查询
        RouteQuery query = new RouteQuery();
        query.setFromCity("北京");
        query.setToCity("广州");
        query.setNeedSameCityPick(true);
        query.setNeedSameCityDeliver(true);

        System.out.println("\n查询条件:");
        System.out.println("从 " + query.getFromCity() + " 到 " + query.getToCity());
        System.out.println("同城提车: " + (query.isNeedSameCityPick() ? "是" : "否"));
        System.out.println("同城送车: " + (query.isNeedSameCityDeliver() ? "是" : "否"));
        System.out.println("------------------------");

        // 5. 执行规划
        List<RoutePlan> plans = router.planRoute(query);

        if (plans.isEmpty()) {
            System.out.println("未找到可用的运输方案。");
        } else {
            for (RoutePlan plan : plans) {
                String planType;
                if (plan.getOptimizeTarget() != null) {
                    planType = "[" + plan.getOptimizeTarget().getDesc() + " (多段中转方案)]";
                } else {
                    TransportModeEnum mainMode = plan.getModeList().stream()
                            .filter(m -> m != TransportModeEnum.DRIVER || plan.getSegments().stream().anyMatch(s -> s.getMode() == m && !s.isLastMile()))
                            .findFirst().orElse(TransportModeEnum.DRIVER);
                    planType = "[备选方案: " + mainMode.getDesc() + "]";
                }

                System.out.println("\n" + planType);
                System.out.println("总费用: ¥" + plan.getTotalPrice());
                System.out.println("总时效: " + plan.getTotalTime() + " 小时");
                System.out.println("总分段: " + plan.getSegmentCount());
                System.out.println("路线详情:");
                for (RouteSegment segment : plan.getSegments()) {
                    String modeDesc = getModeDescription(segment.getMode(), segment.isLastMile());
                    System.out.printf("  段%d: %s -> %s, 方式: %s, 里程: %dkm, 时间: %dh, 费用: ¥%s%n",
                            segment.getSegmentNo(),
                            segment.getFromCity(),
                            segment.getToCity(),
                            modeDesc,
                            segment.getMileage(),
                            segment.getTimeHours(),
                            segment.getPrice());
                }
                System.out.println("------------------------");
            }
        }
    }

    /**
     * 向图中添加一条边
     */
    private static void addEdge(Map<CityPair, List<TransportEdge>> graph, String from, String to, TransportModeEnum mode, int mileage, int time, BigDecimal price) {
        CityPair key = new CityPair(from, to);
        graph.computeIfAbsent(key, k -> new ArrayList<>());

        TransportEdge edge = new TransportEdge();
        edge.setFromCity(from);
        edge.setToCity(to);
        edge.setMode(mode);
        edge.setMileage(mileage);
        edge.setTimeHours(time);
        edge.setPrice(price);
        // 注意：这里我们简化A*算法的启发式，直接使用价格和时间作为权重
        edge.setPriceWeight(price.doubleValue());
        edge.setTimeWeight((double) time);

        graph.get(key).add(edge);
    }

    /**
     * 将运输方式枚举转换为中文描述
     */
    private static String getModeDescription(TransportModeEnum mode, boolean isLastMile) {
        if (isLastMile) {
            return mode.getDesc() + "(同城服务)";
        }
        return mode.getDesc();
    }

    public static void run(String[] args) {
        SpringApplication.run(SmartRouterApplication.class, args);
    }
}