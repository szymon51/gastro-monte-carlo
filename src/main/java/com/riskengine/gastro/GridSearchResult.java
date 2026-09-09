package com.riskengine.gastro;

import java.util.List;

public record GridSearchResult(Product product, RowResult bestRow, List<RowResult> rowResults) {};
