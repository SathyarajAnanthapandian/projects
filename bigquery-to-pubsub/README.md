# BigQuery → Pub/Sub (Dataflow, Java)

Batch Apache Beam pipeline that reads every row of a BigQuery table and publishes
one Pub/Sub message per row:

| BigQuery column  | Pub/Sub message        |
|------------------|------------------------|
| `requestPayload` | message body (UTF-8)   |
| `headers`        | message attributes     |

The topic is supplied at launch time with `outputTopic`.

## Supported column types

**`requestPayload`**: `STRING` or `JSON` is sent as-is. A `STRUCT` is serialized to JSON.

**`headers`** can be any of:

- `STRING` / `JSON` containing a JSON object: `{"Content-Type":"application/json","traceId":"abc"}`
- `STRUCT<...>`: each field becomes an attribute
- `ARRAY<STRUCT<key STRING, value STRING>>` (or `name`/`value`)

Null headers give a message with no attributes. Headers that aren't valid JSON fail the job.

Pub/Sub limits that apply to attributes: at most 100 per message, keys ≤ 256 bytes,
values ≤ 1024 bytes, and keys must not start with `goog`.

## Parameters

| Name            | Required | Default          | Example                               |
|-----------------|----------|------------------|---------------------------------------|
| `outputTopic`   | yes      |                  | `projects/my-project/topics/my-topic` |
| `inputTable`    | one of   |                  | `my-project:my_dataset.my_table`      |
| `query`         | one of   |                  | `SELECT headers, requestPayload FROM \`p.d.t\` WHERE ...` |
| `headersColumn` | no       | `headers`        |                                       |
| `payloadColumn` | no       | `requestPayload` |                                       |

## Build and test

```bash
mvn clean package      # runs unit tests, produces target/bigquery-to-pubsub-bundled-1.0.0.jar
```

## Option A: run as a Flex Template with `gcloud`

```bash
export PROJECT=my-project REGION=us-central1 BUCKET=my-bucket

# once (and after every code change)
./build-template.sh

# each run: the topic is the gcloud --parameters argument
gcloud dataflow flex-template run "bq-to-pubsub-$(date +%Y%m%d-%H%M%S)" \
  --project="$PROJECT" --region="$REGION" \
  --template-file-gcs-location="gs://$BUCKET/templates/bigquery-to-pubsub.json" \
  --temp-location="gs://$BUCKET/temp" \
  --parameters="outputTopic=projects/$PROJECT/topics/my-topic,inputTable=$PROJECT:my_dataset.my_table"
```

`./run-template.sh projects/$PROJECT/topics/my-topic $PROJECT:my_dataset.my_table` does the same.

## Option B: launch directly from Maven

```bash
mvn compile exec:java -Dexec.args=" \
  --runner=DataflowRunner \
  --project=$PROJECT --region=$REGION \
  --tempLocation=gs://$BUCKET/temp \
  --inputTable=$PROJECT:my_dataset.my_table \
  --outputTopic=projects/$PROJECT/topics/my-topic"
```

## IAM for the Dataflow worker service account

- `roles/dataflow.worker`
- `roles/bigquery.dataViewer` on the dataset, and `roles/bigquery.readSessionUser` on the project (Storage Read API)
- `roles/bigquery.jobUser` if you use `query`
- `roles/pubsub.publisher` on the topic
- read/write on the temp bucket
