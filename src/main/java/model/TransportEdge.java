package model;

import enumObject.TransportModeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransportEdge {
    private String fromCity;
    private String toCity;
    private TransportModeEnum mode;
    private int mileage;
    private int timeHours;
    private BigDecimal price;
    private double timeWeight; // 时效权重（A*用）
    private double priceWeight; // 价格权重（A*用）
}