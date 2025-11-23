package enumObject;

/**
 * 优化目标枚举
 */
public enum OptimizeTargetEnum {
    TIME("时效最优"),
    PRICE("价格最优");

    private final String desc;

    OptimizeTargetEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}