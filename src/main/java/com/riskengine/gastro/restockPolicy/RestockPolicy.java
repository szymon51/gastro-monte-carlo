package com.riskengine.gastro.restockPolicy;

public interface RestockPolicy {
    int decideProductionAmount(int currentStock, int day);
}
