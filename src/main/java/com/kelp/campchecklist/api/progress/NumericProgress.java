package com.kelp.campchecklist.api.progress;

/** Uncapped observed progress. Status, rather than this ratio, determines satisfaction. */
public record NumericProgress(double current, double target) {
    public NumericProgress {
        if (!Double.isFinite(current) || current < 0 || !Double.isFinite(target) || target <= 0)
            throw new IllegalArgumentException("Progress requires finite current >= 0 and target > 0");
    }
}
