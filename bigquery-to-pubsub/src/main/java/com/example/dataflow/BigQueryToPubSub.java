package com.example.dataflow;

import com.google.api.services.bigquery.model.TableRow;
import java.util.Arrays;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.TypedRead.Method;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.ParDo;

/**
 * Batch Dataflow pipeline that reads every row of a BigQuery table (or query result) and
 * publishes one Pub/Sub message per row:
 *
 * <ul>
 *   <li>message body = the {@code requestPayload} column
 *   <li>message attributes = the {@code headers} column
 * </ul>
 *
 * <p>The target topic is passed on the command line with {@code --outputTopic}.
 */
public class BigQueryToPubSub {

  public static void main(String[] args) {
    BigQueryToPubSubOptions options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(BigQueryToPubSubOptions.class);
    run(options);
  }

  public static void run(BigQueryToPubSubOptions options) {
    boolean hasTable = !isBlank(options.getInputTable());
    boolean hasQuery = !isBlank(options.getQuery());
    if (hasTable == hasQuery) {
      throw new IllegalArgumentException("Provide exactly one of --inputTable or --query.");
    }

    Pipeline pipeline = Pipeline.create(options);

    BigQueryIO.TypedRead<TableRow> read =
        BigQueryIO.readTableRows().withMethod(Method.DIRECT_READ);
    if (hasTable) {
      read =
          read.from(options.getInputTable())
              .withSelectedFields(
                  Arrays.asList(options.getHeadersColumn(), options.getPayloadColumn()));
    } else {
      read = read.fromQuery(options.getQuery()).usingStandardSql();
    }

    pipeline
        .apply("ReadFromBigQuery", read)
        .apply(
            "ToPubsubMessage",
            ParDo.of(
                new TableRowToPubsubMessageFn(
                    options.getHeadersColumn(), options.getPayloadColumn())))
        .apply("PublishToPubSub", PubsubIO.writeMessages().to(options.getOutputTopic()));

    pipeline.run();
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
