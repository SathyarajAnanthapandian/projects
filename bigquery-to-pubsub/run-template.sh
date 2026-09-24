#!/usr/bin/env bash
# Launches the Flex Template. The Pub/Sub topic is passed as the first argument.
#
# Usage: PROJECT=my-project REGION=us-central1 BUCKET=my-bucket \
#        ./run-template.sh projects/my-project/topics/my-topic my-project:my_dataset.my_table
set -euo pipefail

: "${PROJECT:?set PROJECT}"
: "${REGION:?set REGION}"
: "${BUCKET:?set BUCKET (without gs://)}"
TOPIC="${1:?usage: $0 <projects/P/topics/T> <project:dataset.table>}"
TABLE="${2:?usage: $0 <projects/P/topics/T> <project:dataset.table>}"

gcloud dataflow flex-template run "bq-to-pubsub-$(date +%Y%m%d-%H%M%S)" \
  --project="${PROJECT}" \
  --region="${REGION}" \
  --template-file-gcs-location="gs://${BUCKET}/templates/bigquery-to-pubsub.json" \
  --temp-location="gs://${BUCKET}/temp" \
  --parameters="outputTopic=${TOPIC},inputTable=${TABLE}"
