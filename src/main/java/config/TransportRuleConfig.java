package config;

import enumObject.TransportModeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 业务规则配置类（完全可自定义）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportRuleConfig {
    // 1. 分段数配置
    private int maxSegment = 5; // 最大分段数（默认5段，可改为8段等）

    // 2. 板车单段里程限制（公里）
    private int maxSingleSegmentMileage = 100000;

    // 3. 代驾规则
    private boolean forbidDriverInMiddle = true; // 中间段是否禁止代驾（默认禁止）
    private boolean driverExemptPickDeliverFee = true; // 代驾首尾段是否免提送费（默认免）

    // 4. 同城提送费
    private BigDecimal sameCityPickFee = new BigDecimal("200"); // 同城提车费
    private BigDecimal sameCityDeliverFee = new BigDecimal("200"); // 同城送车费

    // 5. 核心：运输方式拼接规则（前一种方式 → 允许的后一种方式）
    private Map<TransportModeEnum, Set<TransportModeEnum>> allowedModeTransitions = new HashMap<>() {{ // 改为TransportModeEnum
        // 代驾后续只能接板车（禁止代驾+代驾）
        put(TransportModeEnum.DRIVER, new HashSet<>(Set.of(
                TransportModeEnum.BIG_TRUCK
        )));
        // 大板车后续可接板车/代驾（允许板车+代驾）
        put(TransportModeEnum.BIG_TRUCK, new HashSet<>(Set.of(
                TransportModeEnum.BIG_TRUCK,
                TransportModeEnum.SMALL_TRUCK,
                TransportModeEnum.DRIVER
        )));
        // 小板车后续可接大板车（允许板车+代驾）
        put(TransportModeEnum.SMALL_TRUCK, new HashSet<>(Set.of(
                TransportModeEnum.BIG_TRUCK,
                TransportModeEnum.SMALL_TRUCK
        )));
        // 海运/铁路只能单独一段（禁止拼接任何方式）
        put(TransportModeEnum.SHIP, Collections.emptySet());
        put(TransportModeEnum.RAIL, Collections.emptySet());
    }};

    // 扩展：允许动态修改拼接规则
    public void updateModeTransition(TransportModeEnum prevMode, Set<TransportModeEnum> allowedNextModes) { // 改为TransportModeEnum
        this.allowedModeTransitions.put(prevMode, allowedNextModes);
    }
}