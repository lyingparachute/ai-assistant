package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.question.UserQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AssistantRequestTrace {

    private static final Logger log = LoggerFactory.getLogger(AssistantRequestTrace.class);

    private final String correlationId;
    private final List<String> portsInvoked = new ArrayList<>();
    private QuestionRoute route;
    private int ragRetrievalCount;
    private String outcome = "pending";

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

    public void completed(String completedOutcome) {
        this.outcome = completedOutcome;
        log.info(
                "event=assistant_request_completed correlationId={} route={} portsInvoked={} ragRetrievalCount={} outcome={}",
                correlationId,
                route,
                portsInvoked,
                ragRetrievalCount,
                completedOutcome);
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
