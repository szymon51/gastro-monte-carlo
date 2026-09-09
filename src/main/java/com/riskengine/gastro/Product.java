package com.riskengine.gastro;

public record Product(
        String group,
        double price,
        Double supplierCost,
        double rawMaterialCost,
        double productionTimeMinutes,
        int shelfLifeDays,
        double demandMean,
        double demandStdDev,
        double clientWantingFactor,
        double clientWantingFactorSupplier,
        boolean isAvailableFromSupplier
) {
}
