package model;

import enumObject.OptimizeTargetEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RouteQuery {
    private String fromCity;
    private String toCity;
    private boolean needSameCityPick;
    private boolean needSameCityDeliver;
    private OptimizeTargetEnum optimizeTarget;
}