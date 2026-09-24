package com.example.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.bigquery.model.TableRow;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.DoFn;

/**
 * Converts a BigQuery row into a Pub/Sub message.
 *
 * <p>The payload column becomes the message body (UTF-8). STRING and JSON columns are sent as-is;
 * a STRUCT/RECORD column is serialized to JSON.
 *
 * <p>The headers column becomes the message attributes. Supported shapes:
 *
 * <ul>
 *   <li>STRING or JSON holding a JSON object, e.g. {@code {"Content-Type":"application/json"}}
 *   <li>STRUCT/RECORD, e.g. {@code STRUCT<contentType STRING, traceId STRING>}
 *   <li>ARRAY of key/value STRUCTs, e.g. {@code ARRAY<STRUCT<key STRING, value STRING>>} (the
 *       fields may also be named {@code name}/{@code value})
 * </ul>
 *
 * Null headers produce a message with no attributes. Non-string header values are converted to
 * their JSON text.
 */
public class TableRowToPubsubMessageFn extends DoFn<TableRow, PubsubMessage> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String headersColumn;
  private final String payloadColumn;

  private final Counter published = Metrics.counter(TableRowToPubsubMessageFn.class, "rows");
  private final Counter emptyPayload =
      Metrics.counter(TableRowToPubsubMessageFn.class, "rowsWithEmptyPayload");

  public TableRowToPubsubMessageFn(String headersColumn, String payloadColumn) {
    this.headersColumn = headersColumn;
    this.payloadColumn = payloadColumn;
  }

  @ProcessElement
  public void processElement(@Element TableRow row, OutputReceiver<PubsubMessage> out) {
    byte[] payload = toPayload(row.get(payloadColumn));
    if (payload.length == 0) {
      emptyPayload.inc();
    }
    out.output(new PubsubMessage(payload, toAttributes(row.get(headersColumn))));
    published.inc();
  }

  static byte[] toPayload(Object value) {
    if (value == null) {
      return new byte[0];
    }
    String text = value instanceof String ? (String) value : toJson(value);
    return text.getBytes(StandardCharsets.UTF_8);
  }

  static Map<String, String> toAttributes(Object headers) {
    Map<String, String> attributes = new HashMap<>();
    if (headers == null) {
      return attributes;
    }

    if (headers instanceof String) {
      String json = ((String) headers).trim();
      if (json.isEmpty()) {
        return attributes;
      }
      JsonNode node;
      try {
        node = MAPPER.readTree(json);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("headers is not valid JSON: " + json, e);
      }
      if (node.isNull()) {
        return attributes;
      }
      if (!node.isObject()) {
        throw new IllegalArgumentException("headers must be a JSON object, got: " + json);
      }
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        JsonNode v = field.getValue();
        if (!v.isNull()) {
          attributes.put(field.getKey(), v.isTextual() ? v.asText() : v.toString());
        }
      }
      return attributes;
    }

    if (headers instanceof List) {
      for (Object item : (List<?>) headers) {
        if (!(item instanceof Map)) {
          throw new IllegalArgumentException(
              "headers array entries must be STRUCT<key, value>, got: " + item);
        }
        Map<?, ?> entry = (Map<?, ?>) item;
        Object key = entry.containsKey("key") ? entry.get("key") : entry.get("name");
        if (key == null) {
          throw new IllegalArgumentException("headers array entry has no key/name field: " + item);
        }
        putIfNotNull(attributes, key.toString(), entry.get("value"));
      }
      return attributes;
    }

    if (headers instanceof Map) {
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) headers).entrySet()) {
        putIfNotNull(attributes, entry.getKey().toString(), entry.getValue());
      }
      return attributes;
    }

    throw new IllegalArgumentException(
        "Unsupported headers type " + headers.getClass().getName() + ": " + headers);
  }

  private static void putIfNotNull(Map<String, String> attributes, String key, Object value) {
    if (value != null) {
      attributes.put(key, value instanceof String ? (String) value : toJson(value));
    }
  }

  private static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot serialize value to JSON: " + value, e);
    }
  }
}
