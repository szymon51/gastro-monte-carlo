package com.riskengine.gastro.demandModel;

public class ConstantDemandModel implements DemandModel {
    private final double mean;
    private final double stdDev;

    public ConstantDemandModel(double mean, double stdDev) {
        this.mean = mean;
        this.stdDev = stdDev;
    }

    @Override
    public double dailyMean(int day) {
        return mean;
    }

    @Override
    public double dailyStdDev(int day) {
        return stdDev;
    }
}
