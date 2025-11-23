package model;

import enumObject.OptimizeTargetEnum;
import enumObject.TransportModeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoutePlan {
    private String fromCity;
    private String toCity;
    private int segmentCount; // 总分段数
    private List<TransportModeEnum> modeList;
    private List<RouteSegment> segments; // 分段详情
    private int totalTime; // 总时效（小时）
    private BigDecimal totalPrice; // 总价格（元）
    private boolean needSameCityPick; // 是否同城提车
    private boolean needSameCityDeliver; // 是否同城送车
    private OptimizeTargetEnum optimizeTarget;

    private TransportModeEnum firstMainMode;
    private TransportModeEnum lastMainMode;
}