#!/usr/bin/env bash
# Builds the jar and registers it as a Dataflow Flex Template.
#
# Usage: PROJECT=my-project REGION=us-central1 BUCKET=my-bucket ./build-template.sh
set -euo pipefail

: "${PROJECT:?set PROJECT}"
: "${REGION:?set REGION}"
: "${BUCKET:?set BUCKET (without gs://)}"
REPOSITORY="${REPOSITORY:-dataflow-templates}"

IMAGE="${REGION}-docker.pkg.dev/${PROJECT}/${REPOSITORY}/bigquery-to-pubsub:latest"
TEMPLATE="gs://${BUCKET}/templates/bigquery-to-pubsub.json"

mvn -B clean package

gcloud artifacts repositories describe "${REPOSITORY}" \
    --project="${PROJECT}" --location="${REGION}" >/dev/null 2>&1 \
  || gcloud artifacts repositories create "${REPOSITORY}" \
       --project="${PROJECT}" --location="${REGION}" --repository-format=docker

gcloud dataflow flex-template build "${TEMPLATE}" \
  --project="${PROJECT}" \
  --image-gcr-path="${IMAGE}" \
  --sdk-language=JAVA \
  --flex-template-base-image=JAVA17 \
  --metadata-file=metadata.json \
  --jar=target/bigquery-to-pubsub-bundled-1.0.0.jar \
  --env=FLEX_TEMPLATE_JAVA_MAIN_CLASS=com.example.dataflow.BigQueryToPubSub

echo "Template written to ${TEMPLATE}"
