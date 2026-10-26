package experiment7;

import com.drs.analytics.SparkAnalyticsJob;

/**
 * Backward compatibility wrapper for legacy Experiment 7 class name.
 * Forwards execution to {@link SparkAnalyticsJob}.
 */
public class SparkMapReduce {
    public static void main(String[] args) {
        SparkAnalyticsJob.main(args);
    }
}
