package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CityBaseData {
    private String fromCity;
    private String toCity;
    private double fromLat; // 出发地纬度
    private double fromLng; // 出发地经度
    private double toLat;   // 目的地纬度
    private double toLng;   // 目的地经度
    private int mileage;    // 城市对总里程（公里）
}