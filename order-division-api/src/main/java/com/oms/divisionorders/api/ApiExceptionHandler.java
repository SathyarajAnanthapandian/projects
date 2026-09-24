package com.oms.divisionorders.api;

import com.google.cloud.bigquery.BigQueryException;
import com.oms.divisionorders.domain.InvalidDateRangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errors come back as RFC 7807 problem details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidDateRangeException.class)
    ProblemDetail invalidRange(InvalidDateRangeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail badParam(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid value for '" + e.getName() + "'. Dates must be YYYY-MM-DD.");
    }

    /** BigQuery details (SQL, project, table) are logged, not returned to callers. */
    @ExceptionHandler(BigQueryException.class)
    ProblemDetail bigQuery(BigQueryException e) {
        log.error("BigQuery query failed: reason={} message={}",
                e.getError() == null ? null : e.getError().getReason(), e.getMessage(), e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Order data is temporarily unavailable. Try again shortly.");
    }
}
