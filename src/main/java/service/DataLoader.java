package service;

import enumObject.TransportModeEnum;
import model.CityBaseData;
import model.CityPair;
import model.TransportEdgeAttr;

import java.util.Set;

/**
 * 数据加载接口（解耦数据来源：数据库/接口/文件）
 */
public interface DataLoader {
    /**
     * 加载城市基础数据（经纬度、总里程）
     */
    CityBaseData loadCityBaseData(CityPair cityPair);

    /**
     * 加载运输边属性（时效、价格、里程）
     */
    TransportEdgeAttr loadTransportEdgeAttr(TransportModeEnum mode, CityPair cityPair); // 改为TransportModeEnum

    /**
     * 加载所有可能的中转城市（出发地/目的地之外的可选城市）
     */

    Set<String> loadAllDirectlyReachableCities(String fromCity);
}