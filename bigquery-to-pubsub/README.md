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
| `env`           | no       |                  | `dev` (loads `application-dev.properties`) |
| `outputTopic`   | yes      |                  | `projects/my-project/topics/my-topic` |
| `inputTable`    | one of   |                  | `my-project:my_dataset.my_table`      |
| `query`         | one of   |                  | `SELECT headers, requestPayload FROM \`p.d.t\` WHERE ...` |
| `headersColumn` | no       | `headers`        |                                       |
| `payloadColumn` | no       | `requestPayload` |                                       |

## Environments (dev / qa / prod)

Settings live in `src/main/resources`:

| File                          | Purpose                                              |
|-------------------------------|------------------------------------------------------|
| `application.properties`      | shared by all environments (`runner=DirectRunner`, column names) |
| `application-dev.properties`  | dev project, table, topic                            |
| `application-qa.properties`   | qa project, table, topic                             |
| `application-prod.properties` | prod project, table, topic                           |

Pick one with `--env=dev|qa|prod` (or the `APP_ENV` environment variable). Every `key=value`
becomes the pipeline option `--key=value`. Precedence: command line > `application-<env>` >
`application`. Without `--env`, no file is loaded and only command-line arguments are used.

## Run locally in IntelliJ (DirectRunner)

1. Log in once so the pipeline can reach BigQuery and Pub/Sub with your identity:
   ```bash
   gcloud auth application-default login
   ```
   Your account needs read access to the table (plus `bigquery.readSessionUser` on the project)
   and `pubsub.publisher` on the topic.
2. Replace the placeholder values in `src/main/resources/application-<env>.properties`.
3. **File → Open** the `bigquery-to-pubsub` folder (IntelliJ imports it as a Maven project,
   JDK 17+).
4. Pick **BigQueryToPubSub [dev]** (or `[qa]`, `[prod]`) from the run configuration drop-down
   and click Run or Debug. These come from `.run/` in the project.

   To create one by hand: **Run → Edit Configurations → + → Application**, main class
   `com.example.dataflow.BigQueryToPubSub`, program arguments `--env=dev`.

DirectRunner runs the whole pipeline inside the IntelliJ JVM, so you can set breakpoints in
`TableRowToPubsubMessageFn`. It **really publishes** to the configured topic, so point dev at a
test topic. To try something one-off, add arguments after `--env`, e.g.
`--env=dev --outputTopic=projects/my-dev-project/topics/scratch`.

To run the same env on Dataflow instead: `--env=dev --runner=DataflowRunner`
(uses `region` and `tempLocation` from the file).

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
