package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransportEdgeAttr {
    private int mileage;    // 该边里程（公里）
    private int timeHours;  // 时效（小时）
    private BigDecimal price; // 价格（元）
}