package service.impl;


import config.TransportRuleConfig;
import enumObject.OptimizeTargetEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import model.CityBaseData;
import service.HeuristicCalculator;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认启发函数：基于城市直线距离预估剩余成本
 */
public class DefaultHeuristicCalculator implements HeuristicCalculator {

    // 城市经纬度数据，用于计算直线距离
    private final Map<String, CityBaseData> cityPositionData;

    // --- 成本估算参数 (可配置) ---
    // 不同运输方式的预估单价（元/公里）
    private final double DEFAULT_TRUCK_PRICE_PER_KM = 5.0;
    private final double DEFAULT_DRIVER_PRICE_PER_KM = 8.0;
    private final double DEFAULT_RAIL_PRICE_PER_KM = 1.5;
    private final double DEFAULT_SEA_PRICE_PER_KM = 1.0;

    // 不同运输方式的预估时速（公里/小时）
    private final double DEFAULT_TRUCK_SPEED_KMH = 60.0;
    private final double DEFAULT_DRIVER_SPEED_KMH = 50.0;
    private final double DEFAULT_RAIL_SPEED_KMH = 80.0;
    private final double DEFAULT_SEA_SPEED_KMH = 30.0;

    /**
     * 构造函数，注入城市数据
     * @param cityPositionData 包含城市经纬度的Map
     */
    public DefaultHeuristicCalculator(Map<String, CityBaseData> cityPositionData) {
        this.cityPositionData = cityPositionData;
    }

    /**
     * 核心方法：计算从当前城市到目标城市的预估成本（H值）
     *
     * @param currentCity     当前所在城市
     * @param targetCity      目标城市
     * @param optimizeTarget  优化目标（PRICE 或 TIME）
     * @return 预估的成本（H值）
     */
    @Override
    public double calculate(String currentCity, String targetCity, OptimizeTargetEnum optimizeTarget) {
        // 1. 边界情况处理：如果当前城市就是目标城市，预估成本为0
        if (currentCity.equals(targetCity)) {
            return 0.0;
        }

        // 2. 估算当前城市到目标城市的直线距离
        //    这是所有成本预估的基础，因为物理距离是运输成本的主要决定因素之一
        double straightLineDistance = estimateStraightLineDistance(currentCity, targetCity);

        // 3. 如果无法估算距离（例如，某个城市的数据不存在），返回一个很大的默认值
        //    这会让A*算法尽量避免选择这条未知的路径
        if (straightLineDistance < 0) {
            return 1_000_000.0; // 一个足够大的惩罚值
        }

        // 4. 根据不同的优化目标，计算不同的H值
        switch (optimizeTarget) {
            case PRICE:
                return calculatePriceHeuristic(straightLineDistance);
            case TIME:
                return calculateTimeHeuristic(straightLineDistance);
            default:
                // 默认情况下，也返回一个较大的值，防止程序出错
                return 1_000_000.0;
        }
    }

    /**
     * 估算两个城市之间的地球表面直线距离（Haversine公式）
     * @param cityA 城市A
     * @param cityB 城市B
     * @return 距离（公里），如果数据不足则返回-1
     */
    private double estimateStraightLineDistance(String cityA, String cityB) {
        // 从注入的Map中获取两个城市的经纬度数据
        CityBaseData cityAData = cityPositionData.get(cityA);
        CityBaseData cityBData = cityPositionData.get(cityB);

        // 检查数据是否存在
        if (cityAData == null || cityBData == null) {
            return -1.0; // 表示无法计算
        }

        // 提取经纬度（注意：CityBaseData中存储的是弧度）
        double lat1 = cityAData.getFromLat();
        double lon1 = cityAData.getFromLng();
        double lat2 = cityBData.getFromLat();
        double lon2 = cityBData.getFromLng();

        // Haversine公式计算两点间距离
        final double R = 6371; // 地球半径，单位为公里
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // 返回计算出的距离（公里）
        return R * c;
    }

    /**
     * 计算价格预估（H值）
     * @param distance 直线距离（公里）
     * @return 预估价格（元）
     */
    private double calculatePriceHeuristic(double distance) {
        // 策略：选择所有可用运输方式中**最便宜**的单价来计算
        // 这是一个"乐观估计"，确保H值不会高估实际成本，保证A*算法的最优性
        double cheapestPricePerKm = Math.min(
                Math.min(DEFAULT_TRUCK_PRICE_PER_KM, DEFAULT_DRIVER_PRICE_PER_KM),
                Math.min(DEFAULT_RAIL_PRICE_PER_KM, DEFAULT_SEA_PRICE_PER_KM)
        );
        return distance * cheapestPricePerKm;
    }

    /**
     * 计算时间预估（H值）
     * @param distance 直线距离（公里）
     * @return 预估时间（小时）
     */
    private double calculateTimeHeuristic(double distance) {
        // 策略：选择所有可用运输方式中**最快**的速度来计算
        // 同样是一个"乐观估计"，假设能全程使用最快的运输方式
        double fastestSpeedKmH = Math.max(
                Math.max(DEFAULT_TRUCK_SPEED_KMH, DEFAULT_DRIVER_SPEED_KMH),
                Math.max(DEFAULT_RAIL_SPEED_KMH, DEFAULT_SEA_SPEED_KMH)
        );
        return distance / fastestSpeedKmH;
    }

    // --- Getters and Setters for configuration ---
    // 为了提高灵活性，可以为这些默认参数提供 setter 方法
    // 这样在不修改类内部代码的情况下，就可以调整估算策略
    public double getDEFAULT_TRUCK_PRICE_PER_KM() { return DEFAULT_TRUCK_PRICE_PER_KM; }
    public double getDEFAULT_DRIVER_PRICE_PER_KM() { return DEFAULT_DRIVER_PRICE_PER_KM; }
    public double getDEFAULT_RAIL_PRICE_PER_KM() { return DEFAULT_RAIL_PRICE_PER_KM; }
    public double getDEFAULT_SEA_PRICE_PER_KM() { return DEFAULT_SEA_PRICE_PER_KM; }

    public double getDEFAULT_TRUCK_SPEED_KMH() { return DEFAULT_TRUCK_SPEED_KMH; }
    public double getDEFAULT_DRIVER_SPEED_KMH() { return DEFAULT_DRIVER_SPEED_KMH; }
    public double getDEFAULT_RAIL_SPEED_KMH() { return DEFAULT_RAIL_SPEED_KMH; }
    public double getDEFAULT_SEA_SPEED_KMH() { return DEFAULT_SEA_SPEED_KMH; }
}