package model;

import enumObject.TransportModeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AStarNodeKey {
    private String city;
    private int segmentCount;
    private TransportModeEnum previousMode; // 上一次的运输方式
    private TransportModeEnum currentMode; // 当前的运输方式

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AStarNodeKey that = (AStarNodeKey) o;
        return segmentCount == that.segmentCount &&
                Objects.equals(city, that.city) &&
                previousMode == that.previousMode &&
                currentMode == that.currentMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(city, segmentCount, previousMode, currentMode);
    }
}