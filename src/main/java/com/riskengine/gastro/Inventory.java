package com.riskengine.gastro;

import java.util.Arrays;

public class Inventory {
    private final int[] stockByAge;

    public Inventory(int shelfLifeDays) {
        stockByAge = new int[shelfLifeDays];
    }

    public int totalStock() {
        return Arrays.stream(stockByAge).sum();
    }

    public void addNewStock(int amount) {
        stockByAge[0] += amount;
    }

    public int sellFifo(int demand) {
        int amountLeftToCollect = demand;
        for(int i = stockByAge.length - 1; i >= 0; i--) {
            int stockForIthDay = stockByAge[i];
            if(stockForIthDay < amountLeftToCollect) {
                stockByAge[i] = 0;
                amountLeftToCollect -= stockForIthDay;
            } else if(stockForIthDay > amountLeftToCollect) {
                stockByAge[i] = stockForIthDay - amountLeftToCollect;
                return demand;
            } else {
                stockByAge[i] = 0;
                return demand;
            }
        }
        return demand - amountLeftToCollect;
    }

    public int ageByOneDay() {
        int lastIdx = stockByAge.length - 1;
       int waste = stockByAge[lastIdx] ;
       for(int i = lastIdx - 1; i >= 0; i--) stockByAge[i+1] = stockByAge[i];
       stockByAge[0] = 0;
       return waste;
    }

}
