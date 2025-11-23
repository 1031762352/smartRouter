package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CityPair {
    private String fromCity;
    private String toCity;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CityPair cityPair = (CityPair) o;
        return Objects.equals(fromCity, cityPair.fromCity) && Objects.equals(toCity, cityPair.toCity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromCity, toCity);
    }
}