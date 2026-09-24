package com.example.dataflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;

import com.google.api.services.bigquery.model.TableRow;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.beam.sdk.io.gcp.bigquery.TableRowJsonCoder;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessageWithAttributesCoder;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.junit.Rule;
import org.junit.Test;

public class TableRowToPubsubMessageFnTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void jsonStringHeadersBecomeAttributes() {
    Map<String, String> attrs =
        TableRowToPubsubMessageFn.toAttributes(
            "{\"Content-Type\":\"application/json\",\"retry\":3,\"skip\":null}");
    assertThat(attrs, hasEntry("Content-Type", "application/json"));
    assertThat(attrs, hasEntry("retry", "3"));
    assertThat(attrs.size(), equalTo(2));
  }

  @Test
  public void keyValueArrayHeadersBecomeAttributes() {
    List<Map<String, Object>> headers =
        Arrays.asList(entry("key", "traceId", "value", "abc"), entry("name", "x", "value", "y"));
    Map<String, String> attrs = TableRowToPubsubMessageFn.toAttributes(headers);
    assertThat(attrs, hasEntry("traceId", "abc"));
    assertThat(attrs, hasEntry("x", "y"));
  }

  @Test
  public void structHeadersBecomeAttributes() {
    Map<String, Object> headers = entry("contentType", "text/plain", "traceId", "t1");
    Map<String, String> attrs = TableRowToPubsubMessageFn.toAttributes(headers);
    assertThat(attrs, hasEntry("contentType", "text/plain"));
    assertThat(attrs, hasEntry("traceId", "t1"));
  }

  @Test
  public void nullOrBlankHeadersProduceNoAttributes() {
    assertThat(TableRowToPubsubMessageFn.toAttributes(null), anEmptyMap());
    assertThat(TableRowToPubsubMessageFn.toAttributes("  "), anEmptyMap());
    assertThat(TableRowToPubsubMessageFn.toAttributes("null"), anEmptyMap());
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidJsonHeadersFail() {
    TableRowToPubsubMessageFn.toAttributes("not json");
  }

  @Test
  public void structPayloadIsSerializedToJson() {
    byte[] payload = TableRowToPubsubMessageFn.toPayload(entry("a", "1", "b", "2"));
    assertThat(new String(payload, StandardCharsets.UTF_8), equalTo("{\"a\":\"1\",\"b\":\"2\"}"));
  }

  @Test
  public void pipelineConvertsRows() {
    TableRow row =
        new TableRow()
            .set("headers", "{\"traceId\":\"t-1\"}")
            .set("requestPayload", "{\"orderId\":42}");

    PCollection<PubsubMessage> messages =
        pipeline
            .apply(Create.of(Collections.singletonList(row)).withCoder(TableRowJsonCoder.of()))
            .apply(ParDo.of(new TableRowToPubsubMessageFn("headers", "requestPayload")))
            .setCoder(PubsubMessageWithAttributesCoder.of());

    PAssert.that(
            messages.apply(
                MapElements.into(TypeDescriptors.strings())
                    .via(
                        m ->
                            new String(m.getPayload(), StandardCharsets.UTF_8)
                                + "|"
                                + m.getAttribute("traceId"))))
        .containsInAnyOrder("{\"orderId\":42}|t-1");

    pipeline.run().waitUntilFinish();
  }

  private static Map<String, Object> entry(String k1, Object v1, String k2, Object v2) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put(k1, v1);
    m.put(k2, v2);
    return m;
  }
}
