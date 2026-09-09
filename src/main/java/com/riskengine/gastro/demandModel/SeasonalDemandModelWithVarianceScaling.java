package com.riskengine.gastro.demandModel;

public class SeasonalDemandModelWithVarianceScaling implements DemandModel {
    private final double mean;
    private final double stdDev;
    private final double[] dayOfTheWeekMultiplier;

    public SeasonalDemandModelWithVarianceScaling(double mean, double stdDev, double[] dayOfTheWeekMultiplier) {
        this.mean = mean;
        this.stdDev = stdDev;
        this.dayOfTheWeekMultiplier = dayOfTheWeekMultiplier;
    }

    @Override
    public double dailyMean(int day) {
        return mean*dayOfTheWeekMultiplier[day - 1];
    }

    @Override
    public double dailyStdDev(int day) {
        return stdDev*dayOfTheWeekMultiplier[day - 1];
    }
}

