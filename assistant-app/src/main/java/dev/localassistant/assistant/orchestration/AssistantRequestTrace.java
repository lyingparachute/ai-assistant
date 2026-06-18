package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.question.UserQuestion;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
public final class AssistantRequestTrace {

    private static final String OUTCOME_PENDING = "pending";
    private static final String OUTCOME_ANSWERED_FORMAT = "%s_answered_sources=%d";

    private final String correlationId;
    private final List<String> portsInvoked = new ArrayList<>();
    private QuestionRoute route;
    private int ragRetrievalCount;
    private String outcome = OUTCOME_PENDING;

    private AssistantRequestTrace(String correlationId) {
        this.correlationId = correlationId;
    }

    public static AssistantRequestTrace start(UserQuestion question) {
        AssistantRequestTrace trace = new AssistantRequestTrace(UUID.randomUUID().toString());
        log.info(
                "event=assistant_request_started correlationId={} questionLength={}",
                trace.correlationId,
                question.text().length());
        return trace;
    }

    public String correlationId() {
        return correlationId;
    }

    public void routeSelected(QuestionRoute selectedRoute) {
        this.route = selectedRoute;
        log.info(
                "event=assistant_route_selected correlationId={} route={}",
                correlationId,
                selectedRoute);
    }

    public void portInvoked(String portName) {
        portsInvoked.add(portName);
        log.info(
                "event=assistant_port_invoked correlationId={} port={} invocationOrder={}",
                correlationId,
                portName,
                portsInvoked.size());
    }

    public void ragRetrieval(int snippetCount) {
        this.ragRetrievalCount = snippetCount;
        log.info(
                "event=assistant_rag_retrieval correlationId={} snippetCount={}",
                correlationId,
                snippetCount);
    }

    public void completed(int answeredSourceCount) {
        this.outcome =
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

    public List<String> portsInvoked() {
        return List.copyOf(portsInvoked);
    }

    public int ragRetrievalCount() {
        return ragRetrievalCount;
    }

    public String outcome() {
        return outcome;
    }
}
