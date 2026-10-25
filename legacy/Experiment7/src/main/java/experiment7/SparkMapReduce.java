
package experiment7;

import java.util.List;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import scala.Tuple2;

public class SparkMapReduce {

    public static void main(String[] args) {

        // Input and output paths
        String inputFile = "input/rescue_tasks.txt";
        String outputFile = "output";

        // Create Spark configuration
        SparkConf conf = new SparkConf()
                .setAppName("MapReduce Experiment")
                .setMaster("local[*]");

        // Create Spark context
        JavaSparkContext sc = new JavaSparkContext(conf);

        System.out.println("======================================");
        System.out.println(" MapReduce Experiment");
        System.out.println("======================================");

        // Read input file
        JavaRDD<String> lines = sc.textFile(inputFile);

        // Remove empty lines
        JavaRDD<String> validLines = lines.filter(
                line -> !line.trim().isEmpty()
        );

        /*
         * MAP PHASE
         *
         * Input:
         * ClusterA,Medical,5
         *
         * Output:
         * (ClusterA|Medical, 1,5)
         */

        JavaPairRDD<String, String> mappedData = validLines.mapToPair(
                line -> {

                    String[] parts = line.split(",");

                    String cluster = parts[0].trim();
                    String taskType = parts[1].trim();
                    String workload = parts[2].trim();

                    String key = cluster + "|" + taskType;

                    return new Tuple2<>(
                            key,
                            "1," + workload
                    );
                }
        );

        /*
         * REDUCE PHASE
         *
         * Group records having the same key
         * and calculate:
         *
         * Total number of tasks
         * Total workload
         */

        JavaPairRDD<String, String> reducedData =
                mappedData.reduceByKey(
                        (value1, value2) -> {

                            String[] first = value1.split(",");
                            String[] second = value2.split(",");

                            int taskCount =
                                    Integer.parseInt(first[0])
                                    + Integer.parseInt(second[0]);

                            int workload =
                                    Integer.parseInt(first[1])
                                    + Integer.parseInt(second[1]);

                            return taskCount + "," + workload;
                        }
                );

        /*
         * Sort the final results
         */
        JavaPairRDD<String, String> sortedData =
                reducedData.sortByKey();

        /*
         * Display results
         */
        System.out.println();
        System.out.println("Cluster | Task Type | Number of Tasks | Total Workload");
        System.out.println("-------------------------------------------------------");

        List<Tuple2<String, String>> results =
                sortedData.collect();

        for (Tuple2<String, String> result : results) {

            String[] keyParts = result._1.split("\\|");
            String[] values = result._2.split(",");

            String cluster = keyParts[0];
            String taskType = keyParts[1];

            int taskCount = Integer.parseInt(values[0]);
            int workload = Integer.parseInt(values[1]);

            System.out.println(
                    cluster + " | "
                    + taskType + " | "
                    + taskCount + " | "
                    + workload
            );
        }

        /*
         * Save the output
         */
// Output is displayed on the console.
// No need to save it to a file for this experiment.

        System.out.println();
        System.out.println("MapReduce job completed successfully.");
        System.out.println("Output saved in: " + outputFile);

        // Close Spark
        sc.close();
    }
}