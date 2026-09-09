package com.riskengine.gastro.demandModel;

public interface DemandModel {
    double dailyMean(int day);
    double dailyStdDev(int day);
}
