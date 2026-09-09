package com.riskengine.gastro.restockPolicy;

public class BaseStockPolicy implements RestockPolicy {
    private final int targetS;

    public BaseStockPolicy(int targetS) {
        this.targetS = targetS;
    }
    public int decideProductionAmount(int currentStock, int day) {
        return Math.max(0,targetS - currentStock);
    }
}
