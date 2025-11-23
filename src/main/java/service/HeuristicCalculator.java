package service;


import config.TransportRuleConfig;
import enumObject.OptimizeTargetEnum;

/**
 * 启发函数接口（A*算法核心：预估剩余成本）
 */
public interface HeuristicCalculator {
    /**
     * 计算预估剩余成本
     * @param currentCity 当前城市
     * @param targetCity 目标城市
     * @param target 优化目标（时效/价格）
     *
     * @return 预估成本（时效→小时，价格→元）
     */
    double calculate(String currentCity, String targetCity, OptimizeTargetEnum target);
}