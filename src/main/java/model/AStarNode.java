package model;

import enumObject.TransportModeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AStarNode {
    private String city; // 当前城市
    private int segmentCount; // 已走分段数
    private TransportModeEnum previousMode; // 上一次的运输方式
    private TransportModeEnum currentMode; // 当前的运输方式
    private double gScore; // 已消耗成本（G值）
    private double hScore; // 预估剩余成本（H值）
    private AStarNode prevNode; // 前驱节点（回溯路径用）
    private TransportEdge edge; // 当前边（构建路径详情用）

    // F值 = G + H（A*排序核心）
    public double getFScore() {
        return gScore + hScore;
    }
}