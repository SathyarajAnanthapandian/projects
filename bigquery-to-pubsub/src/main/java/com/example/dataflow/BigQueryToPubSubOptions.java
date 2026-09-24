package com.example.dataflow;

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation;

/** Command-line arguments for {@link BigQueryToPubSub}. */
public interface BigQueryToPubSubOptions extends PipelineOptions {

  @Description(
      "BigQuery table to read, in the form project:dataset.table or project.dataset.table. "
          + "Provide either inputTable or query.")
  String getInputTable();

  void setInputTable(String value);

  @Description(
      "Standard SQL query to read from instead of a whole table. "
          + "Provide either inputTable or query.")
  String getQuery();

  void setQuery(String value);

  @Description("Pub/Sub topic to publish to, in the form projects/<project>/topics/<topic>.")
  @Validation.Required
  String getOutputTopic();

  void setOutputTopic(String value);

  @Description("Column holding the headers; each header is published as a message attribute.")
  @Default.String("headers")
  String getHeadersColumn();

  void setHeadersColumn(String value);

  @Description("Column holding the request payload; published as the message body.")
  @Default.String("requestPayload")
  String getPayloadColumn();

  void setPayloadColumn(String value);
}
