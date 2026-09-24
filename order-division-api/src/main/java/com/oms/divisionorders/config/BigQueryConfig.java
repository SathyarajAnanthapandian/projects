package com.oms.divisionorders.config;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableCaching
@EnableConfigurationProperties(OrderEventsProperties.class)
public class BigQueryConfig {

    /**
     * Uses Application Default Credentials: the workload identity / service account
     * on GKE or Cloud Run, or {@code gcloud auth application-default login} locally.
     * The account needs roles/bigquery.jobUser on the job project and
     * roles/bigquery.dataViewer on the dataset.
     *
     * @param jobProjectId project that runs (and pays for) the query jobs. Defaults to
     *     the ADC project. It can differ from the project that owns the table.
     */
    @Bean
    BigQuery bigQuery(@Value("${oms.bigquery.job-project-id:}") String jobProjectId) {
        BigQueryOptions.Builder options = BigQueryOptions.newBuilder();
        if (StringUtils.hasText(jobProjectId)) {
            options.setProjectId(jobProjectId);
        }
        return options.build().getService();
    }
}
