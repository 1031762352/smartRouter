package enumObject;

/**
 * 运输方式枚举
 */
public enum TransportModeEnum {
    SHIP("海运"),
    RAIL("铁路"),
    DRIVER("代驾"),
    BIG_TRUCK("大板车"),
    SMALL_TRUCK("小板车");

    private final String desc;

    TransportModeEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}