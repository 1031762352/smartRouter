package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RouteResult {
    private RoutePlan timeOptimalPlan; // 时效最优
    private RoutePlan priceOptimalPlan; // 价格最优

    private RoutePlan shipPlan ;
    private RoutePlan railPlan ;
    private RoutePlan smallTruckPlan ;
}