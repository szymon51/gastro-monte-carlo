package com.riskengine.gastro.controller;

import com.riskengine.gastro.GridSearchResult;
import com.riskengine.gastro.Product;
import com.riskengine.gastro.RowResult;
import com.riskengine.gastro.SimulationService;
import com.riskengine.gastro.demandModel.DemandModel;
import com.riskengine.gastro.demandModel.SeasonalDemandModelWithVarianceScaling;
import com.riskengine.gastro.restockPolicy.RestockPolicy;
import com.riskengine.gastro.restockPolicy.WeeklyVaryingStockPolicy;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO:
// - S per dzień tygodnia (RestockPolicy jako Strategy Pattern), nie jeden wspólny target
// - Model popytu z korelacją między produktami (pogoda jako wspólny czynnik) lub grubszym ogonem
// - Enum zamiast String dla nazwy produktu (bezpieczeństwo typów)
// - Cache wyników gridSearch (dziś liczone od zera przy każdym request)
// - Testy jednostkowe dla simulateOneDayWithInventory (deterministyczne, bez losowania)
@Controller
public class SimulationController {

    private static final double[] DEMAND_MULTIPLIER_BY_DAY = {0.31, 0.62, 0.93, 1.30, 1.85};
    private static final int WORKING_DAYS = 5;

    @GetMapping("/")
    public String simulate(@RequestParam(defaultValue = "ciasta") String product,
                           @RequestParam(required = false) Integer targetDay1,
                           @RequestParam(required = false) Integer targetDay2,
                           @RequestParam(required = false) Integer targetDay3,
                           @RequestParam(required = false) Integer targetDay4,
                           @RequestParam(required = false) Integer targetDay5,

                           @RequestParam(required = false) Integer demandAdj1,
                           @RequestParam(required = false) Integer demandAdj2,
                           @RequestParam(required = false) Integer demandAdj3,
                           @RequestParam(required = false) Integer demandAdj4,
                           @RequestParam(required = false) Integer demandAdj5,
                           Model model) {
        // TODO: cachowanie wyników symulacji.
        // Problem: każde żądanie (zmiana produktu/modelu) przelicza grid search od nowa,
        // mimo że dla tych samych parametrów wynik zawsze będzie (w granicach szumu Monte Carlo) ten sam.
        // Rozwiązanie: Map<CacheKey, GridSearchResult> w pamięci (np. ConcurrentHashMap),
        // gdzie CacheKey to record(product, demandModelType, ...) — sprawdzić cache przed odpaleniem gridSearch,
        // zapisać wynik po policzeniu. Do zrobienia po ustabilizowaniu reszty UI.

        Product p = resolveProduct(product);
        model.addAttribute("unitCost", SimulationService.unitCost(p));
        model.addAttribute("selectedProduct", product);

        int min = (int) Math.round(p.demandMean() * 0.5);
        // TODO: zakres [min,max] dla stockOptions liczony jako stały mnożnik mean (0.5x-4.0x).
        // Ryzyko: jeśli prawdziwe optimum leży poza tym zakresem, gridSearch go nie znajdzie,
        // a wynik będzie fałszywie wskazywał na S=max jako "najlepsze" (bo to tylko brzeg testowanego zakresu).
        // Solidniejsze rozwiązanie: sprawdzić czy bestProduction wypadło na min/max i jeśli tak,
        // automatycznie rozszerzyć zakres i przeliczyć ponownie.
        int max = (int) Math.round(p.demandMean() * 4.0);
        int step = Math.max(1, (max - min) / 6);
        int[] coarseOptions = buildOptions(min, max, step);

        int[] demandAdjByDay = {
                demandAdj1 != null ? demandAdj1 : 0,
                demandAdj2 != null ? demandAdj2 : 0,
                demandAdj3 != null ? demandAdj3 : 0,
                demandAdj4 != null ? demandAdj4 : 0,
                demandAdj5 != null ? demandAdj5 : 0
        };
        model.addAttribute("demandAdjByDay", demandAdjByDay);

        boolean hasScenario = Arrays.stream(demandAdjByDay).anyMatch(v -> v != 0);
        model.addAttribute("hasScenario", hasScenario);

        double[] adjustedMultiplier = new double[WORKING_DAYS];
        double[] effectiveDemandByDay = new double[WORKING_DAYS];
        for (int i = 0; i < WORKING_DAYS; i++) {
            adjustedMultiplier[i] = DEMAND_MULTIPLIER_BY_DAY[i] * (1.0 + demandAdjByDay[i] / 100.0);
            effectiveDemandByDay[i] = p.demandMean() * adjustedMultiplier[i];
        }
        model.addAttribute("effectiveDemandByDay", effectiveDemandByDay);

        double[] baseDemandByDay = new double[WORKING_DAYS];
        for (int i = 0; i < WORKING_DAYS; i++) {
            baseDemandByDay[i] = p.demandMean() * DEMAND_MULTIPLIER_BY_DAY[i];
        }
        model.addAttribute("baseDemandByDay", baseDemandByDay);

        long start = System.nanoTime();

        // --- Wynik główny: zawsze liczony na bieżącym (ewentualnie zmodyfikowanym) popycie ---
        DemandModel demandModel = new SeasonalDemandModelWithVarianceScaling(p.demandMean(), p.demandStdDev(), adjustedMultiplier);

        GridSearchResult coarseResult = SimulationService.gridSearch(coarseOptions, p, demandModel, 5000);

        int refineMin = Math.max(min, coarseResult.bestRow().baseStock() - step);
        int refineMax = Math.min(max, coarseResult.bestRow().baseStock() + step);
        int[] fineOptions = buildOptions(refineMin, refineMax, 1);
        GridSearchResult fineResult = SimulationService.gridSearch(fineOptions, p, demandModel, 20_000);

        model.addAttribute("coarseResult", coarseResult);
        model.addAttribute("fineResult", fineResult);

        // --- Baseline: dodatkowy wynik z oryginalnym popytem, tylko gdy scenariusz aktywny ---
        GridSearchResult baselineFineResult = null;
        if (hasScenario) {
            DemandModel baselineDemandModel = new SeasonalDemandModelWithVarianceScaling(p.demandMean(), p.demandStdDev(), DEMAND_MULTIPLIER_BY_DAY);

            GridSearchResult baselineCoarseResult = SimulationService.gridSearch(coarseOptions, p, baselineDemandModel, 5000);

            int baselineRefineMin = Math.max(min, baselineCoarseResult.bestRow().baseStock() - step);
            int baselineRefineMax = Math.min(max, baselineCoarseResult.bestRow().baseStock() + step);
            int[] baselineFineOptions = buildOptions(baselineRefineMin, baselineRefineMax, 1);
            baselineFineResult = SimulationService.gridSearch(baselineFineOptions, p, baselineDemandModel, 20_000);
        }
        model.addAttribute("baselineFineResult", baselineFineResult);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        model.addAttribute("elapsedMs", elapsedMs);

        // --- Strategia użytkownika (S per dzień) ---
        boolean hasUserPolicy = targetDay1 != null && targetDay2 != null && targetDay3 != null
                && targetDay4 != null && targetDay5 != null;

        if (hasUserPolicy) {
            int[] targetByDay = {targetDay1, targetDay2, targetDay3, targetDay4, targetDay5};
            RestockPolicy userPolicy = new WeeklyVaryingStockPolicy(targetByDay);
            RowResult userResult = SimulationService.simulateWeekWithPolicy(userPolicy, p, demandModel, 20_000, 0);
            model.addAttribute("userResult", userResult);
            model.addAttribute("userTargetByDay", targetByDay);
        }
        return "results";
    }

    private Product resolveProduct(String name) {
        return switch (name) {
            case "sloiki" -> new Product("sloiki", 50, 30., 15, 8, 60, 20, 10, 0.65, 0.50, true);
            case "pierogi" -> new Product("pierogi", 30, null, 12, 20, 4, 100, 30, 0.50, 0, false);
            case "ciasta" -> new Product("ciasta", 200, 160.0, 60, 30, 2, 4, 2, 0.06, 0.03, true);
            default -> throw new IllegalStateException("Unexpected value: " + name);
        };
    }

    private int[] buildOptions(int min, int max, int step) {
        List<Integer> res = new ArrayList<>();
        for (int i = min; i <= max; i += step) {
            res.add(i);
        }
        return res.stream().mapToInt(Integer::intValue).toArray();
    }
}