package org.learn.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestRunFinishedEvent(
        String eventType,
        UUID testRunId,
        UUID projectId,
        String status,
        OffsetDateTime finishedAt,
        Summary summary,
        UUID triggeredBy
) {
    public static final String TYPE = "TestRunFinished";

    public static TestRunFinishedEvent of(UUID testRunId, UUID projectId, String status,
                                          OffsetDateTime finishedAt, Summary summary, UUID triggeredBy) {
        return new TestRunFinishedEvent(TYPE, testRunId, projectId, status, finishedAt, summary, triggeredBy);
    }

    public record Summary(int total, int passed, int failed, int blocked) {}
}
