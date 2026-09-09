package com.riskengine.gastro;

import com.riskengine.gastro.demandModel.DemandModel;
import com.riskengine.gastro.restockPolicy.BaseStockPolicy;
import com.riskengine.gastro.restockPolicy.RestockPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SimulationService {
    private static final double HOURLY_WAGE = 40;
    private static final double WAGE_PER_MINUTE = HOURLY_WAGE / 60;
    private static final double RISK_AVERSION = 0.5;
    private static final int WORKING_DAYS = 5;

    private static double stdDev(double[] data) {
        double mean = Arrays.stream(data).average().getAsDouble();
        double variance = Arrays.stream(data).map(x -> Math.pow(x - mean, 2)).average().getAsDouble();
        return Math.sqrt(variance);
    }

    private static double percentile(double[] data, double percentile) {
        double[] copy = Arrays.copyOf(data, data.length);
        Arrays.sort(copy);
        int index = (int) (percentile/100 * data.length);
        return copy[Math.min(data.length -1, index)];
    }

    private static double cvar(double[] data, double percentile) {
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        return Arrays.stream(sortedData).limit((long) (sortedData.length * percentile/100)).average().getAsDouble();
    }

    private static DayResult simulateOneDayWithInventory(Product p, Inventory inv, int toProduceQty, double demand) {
        int wasted = inv.ageByOneDay();
        inv.addNewStock(toProduceQty);

        int sold = inv.sellFifo((int) Math.round(demand));
        double revenue = sold * p.price();
        double cost = toProduceQty * ((p.rawMaterialCost() + WAGE_PER_MINUTE * p.productionTimeMinutes()));
        double profit = revenue - cost;
        return new DayResult(profit, sold, wasted, toProduceQty);
    }

    private static WeekResult simulateOneWeek(Product p, RestockPolicy restockPolicy, Random rng, DemandModel demandModel) {
        Inventory inv = new Inventory(p.shelfLifeDays());
        double totalProfit = 0;
        int totalWasted = 0;
        int totalSold = 0;
        int totalProduced = 0;
        int[] producedByDay = new int[WORKING_DAYS];
        double totalDemand = 0;
        int stockOutDays = 0;

        for(int day = 1; day <= WORKING_DAYS; day++) {
            double demand = Math.max(0, rng.nextGaussian(demandModel.dailyMean(day), demandModel.dailyStdDev(day)));
            totalDemand += demand;
            DayResult result = simulateOneDayWithInventory(p, inv, restockPolicy.decideProductionAmount(inv.totalStock(), day), demand);
            totalProfit += result.profit();
            totalProduced += result.produced();
            totalWasted += result.wasted();
            totalSold += result.sold();
            producedByDay[day -1] = result.produced();
            if(result.sold() < demand && demand - result.sold() > 0.5) stockOutDays++;

        }
        int endOfWeekLeftOver = inv.totalStock();
        totalWasted += endOfWeekLeftOver;

        return new WeekResult(totalProduced, totalWasted, totalSold, totalProfit, producedByDay, totalDemand, stockOutDays);
    }

    private static WeekResult[] runMonteCarloWeekly(Product p, RestockPolicy restockPolicy, DemandModel demandModel, int iterations) {
        Random rng = new Random(42);
        WeekResult[] result = new WeekResult[iterations];
        for(int i = 0; i < iterations; i++) {
            result[i] = simulateOneWeek(p, restockPolicy, rng, demandModel);
        }
        return result;
    }

    public static GridSearchResult gridSearch(int[] productionAmountPossibilities, Product p, DemandModel demandModel, int iterations) {
        RowResult bestRow = null;
        List<RowResult> rowResults = new ArrayList<>();

        for(int baseStock : productionAmountPossibilities) {
            RowResult currentRow = simulateWeekWithPolicy(new BaseStockPolicy(baseStock), p, demandModel, iterations, baseStock);
            rowResults.add(currentRow);
            if(bestRow == null || currentRow.target() > bestRow.target()) {
                bestRow = currentRow;
            }
        }
        return new GridSearchResult(p, bestRow, rowResults);
    }

    public static RowResult simulateWeekWithPolicy(RestockPolicy policy, Product p, DemandModel demandModel, int iterations, int baseStock) {
        WeekResult[] sample = runMonteCarloWeekly(p, policy, demandModel, iterations);
        return generateRowResult(sample, baseStock);
    }

    private static RowResult generateRowResult(WeekResult[] sample, int baseStock) {
        double[] sampleProfits = Arrays.stream(sample).mapToDouble(WeekResult::totalProfit).toArray();
        double mean = Arrays.stream(sampleProfits).average().getAsDouble();
        double stdDev = stdDev(sampleProfits);
        double cVar5 = cvar(sampleProfits, 5.0);
        double target = mean - RISK_AVERSION * stdDev;
        double avgWasted = Arrays.stream(sample).mapToInt(WeekResult::totalWasted).average().getAsDouble();
        double[] avgProducedByDay = new double[WORKING_DAYS];
        for (int day = 0; day < WORKING_DAYS; day++) {
            final int d = day;
            avgProducedByDay[day] = Arrays.stream(sample).mapToInt(w -> w.producedByDay()[d]).average().getAsDouble();
        }
        double avgDemand = Arrays.stream(sample).mapToDouble(WeekResult::totalDemand).average().getAsDouble();
        double avgStockOutDaysPerWeek = Arrays.stream(sample).mapToInt(WeekResult::stockOutDays).average().getAsDouble();
        double stockoutProbability = avgStockOutDaysPerWeek / WORKING_DAYS;
        double avgSold = Arrays.stream(sample).mapToDouble(WeekResult::totalSold).average().getAsDouble();
        return new RowResult(baseStock, mean, stdDev, target, cVar5, avgWasted, avgProducedByDay, avgDemand, stockoutProbability, avgSold);
    }

    public static double unitCost(Product p) {
        return p.rawMaterialCost() + WAGE_PER_MINUTE * p.productionTimeMinutes();
    }

    private static void printComparisonTable(GridSearchResult gridSearchResult) {
        for(RowResult rowResult : gridSearchResult.rowResults()) {
            System.out.printf("codzienne bazowe zatowarowanie=%4dszt | srednia zysku tygodniowego=%8.1fzl | odchylenie zyskow pomiedzy tygodniami=%8.1fzl | cel=%8.1f | zysk w najgorszych 5procentach przypadkow=%8.1fzl%n", rowResult.baseStock(), rowResult.mean(), rowResult.stdDev(), rowResult.target(), rowResult.cVar5());
        }
        System.out.printf("najlepsze bazowe zatowarowanie=%4dszt | najlepszy wynik po odjeciu ryzyka=%8.1fzl%n%n", gridSearchResult.bestRow().baseStock(), gridSearchResult.bestRow().target());
    }
}

// ====== OLD MAIN ====================
//        RandomGenerator rng = new Random();
//        double demand = Math.max(0, rng.nextGaussian(30, 12));
//        System.out.println("ciasta: " + simulateOneDay(ciasta, 30, demand));
//
//        long start = System.nanoTime();
//        double[] samp = runMonteCarlo(ciasta, 20, 30, 12,20000);
//        long end = System.nanoTime();
//        System.out.println("czas: " + (end - start) / 1_000_000.0 + " ms");
//        System.out.println("samp min: " + Arrays.stream(samp).min().getAsDouble() + " samp max: " + Arrays.stream(samp).max().getAsDouble() + " samp avg: " + Arrays.stream(samp).average().getAsDouble());
//        System.out.println(Arrays.stream(samp).distinct().count());
//
//        System.out.println("dni z sufitem 3600: " + Arrays.stream(samp).filter(x -> x == 3600).count());
//        System.out.println("stddev: " + stdDev(samp));
//        System.out.println("95 percentyl: " + percentile(samp, 95.0));
//        System.out.println("5 percentyl: " + percentile(samp, 5.0));
//        System.out.println("cvar .5 : " + cvar(samp, 5.0));


//        double bestTarget = Double.NEGATIVE_INFINITY;
//        double bestProduction = -1;
//        for(int production = 10; production <= 60; production+=5) {
//            double[] sample = runMonteCarlo(ciasta, production, 30, 12, 20000);
//            double mean = Arrays.stream(sample).average().getAsDouble();
//            double stdDev = stdDev(sample);
//            double cvar5 = cvar(sample, 5.0);
//            double target = mean - RISK_AVERSION * stdDev;
//            if(target > bestTarget) {
//                bestTarget = target;
//                bestProduction = production;
//            }
//            System.out.printf("produkcja=%3d | srednia=%8.1f | odchylenie std=%8.1f | cel=%8.1f | cvar5=%8.1f%n", production, mean, stdDev, target, cvar5);
//        }
//        System.out.println("Najlepsza produkcja " + bestProduction + " z celem " + bestTarget);
//
//private static double[] runMonteCarlo(com.riskengine.gastro.Product p, int plannedProduction, double demandMean, double demandStdDev, int iterations) {
//    Random rng = new Random();
//    double[] results = new double[iterations];
//    for(int i = 0; i < iterations; i++) {
//        results[i] = simulateOneDay(p, plannedProduction, Math.max(0, rng.nextGaussian()*demandStdDev + demandMean));
//    }
//    return results;
//}
//private static double simulateOneDay(com.riskengine.gastro.Product p, int plannedProduction, double demand) {
//    double amountSold = Math.min(demand, plannedProduction);
//    double revenue = amountSold * p.price();
//    double cost = plannedProduction * (p.rawMaterialCost() + p.productionTimeMinutes() * WAGE_PER_MINUTE);
//    return revenue - cost;
//}

