package com.riskengine.gastro;

public record RowResult(int baseStock, double mean, double stdDev, double target, double cVar5, double avgWasted, double[] avgProducedByDay,
                        double avgDemand, double stockoutProbability, double avgSold) {
}
