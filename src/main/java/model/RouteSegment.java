package model;

import enumObject.TransportModeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RouteSegment {
    private int segmentNo;
    private String fromCity;
    private String toCity;
    private TransportModeEnum mode;
    private int mileage;
    private int timeHours;
    private BigDecimal price;
    private boolean isLastMile; // 新增：是否为末端服务（同城提车/送车）

}