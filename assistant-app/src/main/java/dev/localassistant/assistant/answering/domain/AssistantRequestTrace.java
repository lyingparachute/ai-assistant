package dev.localassistant.assistant.answering.domain;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
final class AssistantRequestTrace {

    private static final String OUTCOME_PENDING = "pending";
    private static final String OUTCOME_ANSWERED_FORMAT = "%s_answered_sources=%d";

    private final String correlationId;
    private final List<String> portsInvoked = new ArrayList<>();
    private QuestionRoute route;
    private int ragRetrievalCount;
    private String outcome = OUTCOME_PENDING;

    private AssistantRequestTrace(final String correlationId) {
        this.correlationId = correlationId;
    }

    static AssistantRequestTrace start(final UserQuestion question) {
        final var trace = new AssistantRequestTrace(UUID.randomUUID().toString());
        log.info(
            "event=assistant_request_started correlationId={} questionLength={}",
            trace.correlationId,
            question.text().length());
        return trace;
    }

    String correlationId() {
        return correlationId;
    }

    void routeSelected(final QuestionRoute selectedRoute) {
        route = selectedRoute;
        log.info(
            "event=assistant_route_selected correlationId={} route={}",
            correlationId,
            selectedRoute);
    }

    void portInvoked(final String portName) {
        portsInvoked.add(portName);
        log.info(
            "event=assistant_port_invoked correlationId={} port={} invocationOrder={}",
            correlationId,
            portName,
            portsInvoked.size());
    }

    void ragRetrieval(final int snippetCount) {
        ragRetrievalCount = snippetCount;
        log.info(
            "event=assistant_rag_retrieval correlationId={} snippetCount={}",
            correlationId,
            snippetCount);
    }

    void completed(final int answeredSourceCount) {
        outcome =
            OUTCOME_ANSWERED_FORMAT.formatted(
                route.name().toLowerCase(Locale.ROOT), answeredSourceCount);
        log.info(
            "event=assistant_request_completed correlationId={} route={} portsInvoked={} ragRetrievalCount={} outcome={}",
            correlationId,
            route,
            portsInvoked,
            ragRetrievalCount,
            outcome);
    }

    List<String> portsInvoked() {
        return List.copyOf(portsInvoked);
    }

    int ragRetrievalCount() {
        return ragRetrievalCount;
    }

    String outcome() {
        return outcome;
    }
}
