package com.riskengine.gastro;

public record WeekResult(int totalProduced, int totalWasted, int totalSold, double totalProfit, int[] producedByDay, double totalDemand, int stockOutDays) {}
