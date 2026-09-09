package com.riskengine.gastro.restockPolicy;

public class WeeklyVaryingStockPolicy implements RestockPolicy {
    private final int[] targetByDay; // 0 to poniedzialek, 6 to niedziela

    public WeeklyVaryingStockPolicy(int[] targetByDay) {
        this.targetByDay = targetByDay;
    }

    @Override
    public int decideProductionAmount(int currentStock, int day) {
        return Math.max(targetByDay[day - 1] - currentStock, 0);
    }
}
