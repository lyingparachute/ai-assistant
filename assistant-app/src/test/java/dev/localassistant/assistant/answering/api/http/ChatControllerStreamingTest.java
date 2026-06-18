package dev.localassistant.assistant.answering.api.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.assistant.answering.domain.AnswerSource;
import dev.localassistant.assistant.answering.domain.AssistantAnswer;
import dev.localassistant.assistant.answering.domain.AssistantResponseSink;
import dev.localassistant.assistant.answering.domain.ConversationTurn;
import dev.localassistant.assistant.answering.domain.SourceContributionStatus;
import dev.localassistant.assistant.answering.domain.SourceType;
import dev.localassistant.assistant.answering.domain.UserQuestion;
import dev.localassistant.assistant.answering.domain.port.inbound.AnswerQuestion;
import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerStreamingTest {

    private static final AssistantChatProperties CHAT_PROPERTIES = new AssistantChatProperties(150, 4, 32);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private AnswerQuestion answerQuestion;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        Executor directExecutor = Runnable::run;
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ChatController(
                                        answerQuestion,
                                        new ChatHttpMapper(),
                                        CHAT_PROPERTIES,
                                        directExecutor))
                        .setControllerAdvice(new HttpExceptionHandler())
                        .setMessageConverters(new MappingJackson2HttpMessageConverter())
                        .setValidator(validator)
                        .setAsyncRequestTimeout(5_000)
                        .build();
    }

    @Test
    void streamsFinalEventWithStructuredAnswer() throws Exception {
        when(answerQuestion.execute(any(AnswerQuestion.Command.class)))
                .thenAnswer(
                        invocation -> {
                            AnswerQuestion.Command command = invocation.getArgument(0);
                            AssistantResponseSink sink = command.sink();
                            ConversationTurn turn =
                                    new ConversationTurn(
                                            UserQuestion.of("What is the capital city of Germany?"),
                                            AssistantAnswer.withTrace(
                                                    "The capital of Germany is Berlin.",
                                                    List.of(
                                                            AnswerSource.CountriesFacts.used(
                                                                    new CountryInfo(
                                                                            "Germany",
                                                                            "Berlin",
                                                                            "Europe",
                                                                            83_240_525L))),
                                                    "trace-123"));
                            sink.recordSourceOutcome(
                                    SourceType.COUNTRIES_FACTS,
                                    SourceContributionStatus.USED);
                            sink.complete(turn);
                            return turn;
                        });

        MvcResult asyncStarted =
                mockMvc.perform(
                                post("/api/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"What is the capital city of Germany?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        MvcResult completed =
                mockMvc.perform(asyncDispatch(asyncStarted))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                        .andReturn();

        List<SseEvent> events = parseSseEvents(completed.getResponse().getContentAsString());
        assertThat(events).extracting(SseEvent::name).containsExactly("trace", "final");

        JsonNode trace = OBJECT_MAPPER.readTree(events.get(0).data());
        assertThat(trace.get("type").asText()).isEqualTo("countries_facts");
        assertThat(trace.get("status").asText()).isEqualTo("USED");

        JsonNode finalPayload = OBJECT_MAPPER.readTree(events.get(1).data());
        assertThat(finalPayload.get("answerText").asText()).isEqualTo("The capital of Germany is Berlin.");
        assertThat(finalPayload.get("traceCorrelationId").asText()).isEqualTo("trace-123");
        assertThat(finalPayload.get("sources").get(0).get("type").asText()).isEqualTo("countries_facts");
        assertThat(finalPayload.get("sources").get(0).get("status").asText()).isEqualTo("USED");
        assertThat(finalPayload.get("sources").get(0).get("countryInfo").get("capital").asText())
                .isEqualTo("Berlin");
    }

    @Test
    void blankQuestionReturnsBadRequestJson() throws Exception {
        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("question must not be blank"));
    }

    @Test
    void malformedJsonReturnsBadRequestJson() throws Exception {
        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content("{\"question\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("request body is not valid JSON"));
    }

    @Test
    void unexpectedFailureEmitsErrorEventOverOpenStream() throws Exception {
        String internalDetail = "AnswerSource invariant violated: status must be USED";
        when(answerQuestion.execute(any(AnswerQuestion.Command.class)))
                .thenThrow(new IllegalArgumentException(internalDetail));

        MvcResult asyncStarted =
                mockMvc.perform(
                                post("/api/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"What is the capital city of Germany?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        MvcResult completed =
                mockMvc.perform(asyncDispatch(asyncStarted))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                        .andReturn();

        List<SseEvent> events = parseSseEvents(completed.getResponse().getContentAsString());
        assertThat(events).extracting(SseEvent::name).containsExactly("error");

        JsonNode error = OBJECT_MAPPER.readTree(events.get(0).data());
        assertThat(error.get("error").asText()).isEqualTo("internal_error");
        assertThat(error.get("message").asText()).isEqualTo("an unexpected error occurred");
        assertThat(completed.getResponse().getContentAsString()).doesNotContain(internalDetail);
    }

    @Test
    void rejectedExecutorEmitsErrorEventOverOpenStream() throws Exception {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ChatController(
                                        answerQuestion,
                                        new ChatHttpMapper(),
                                        CHAT_PROPERTIES,
                                        rejectingExecutor))
                        .setControllerAdvice(new HttpExceptionHandler())
                        .setMessageConverters(new MappingJackson2HttpMessageConverter())
                        .build();

        MvcResult asyncStarted =
                mockMvc.perform(
                                post("/api/chat")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"What is the capital city of Germany?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        MvcResult completed =
                mockMvc.perform(asyncDispatch(asyncStarted))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                        .andReturn();

        List<SseEvent> events = parseSseEvents(completed.getResponse().getContentAsString());
        assertThat(events).extracting(SseEvent::name).containsExactly("error");
        verify(answerQuestion, never()).execute(any(AnswerQuestion.Command.class));
    }

    @Test
    void cancelledTaskDoesNotRunUseCase() {
        SseAssistantResponseSink sink =
                new SseAssistantResponseSink(new SseEmitter(), new ChatHttpMapper());
        ChatStreamTask task =
                new ChatStreamTask(
                        answerQuestion,
                        UserQuestion.of("What is the capital city of Germany?"),
                        sink);

        task.cancel();
        task.run();

        verify(answerQuestion, never()).execute(any(AnswerQuestion.Command.class));
    }

    @Test
    void cancellingRunningTaskInterruptsWorkerAndStopsTheRequest() throws Exception {
        CountDownLatch enteredUseCase = new CountDownLatch(1);
        CountDownLatch observedInterrupt = new CountDownLatch(1);
        when(answerQuestion.execute(any(AnswerQuestion.Command.class)))
                .thenAnswer(invocation -> {
                    enteredUseCase.countDown();
                    while (!Thread.currentThread().isInterrupted()) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                    }
                    observedInterrupt.countDown();
                    return null;
                });
        SseAssistantResponseSink sink =
                new SseAssistantResponseSink(new SseEmitter(), new ChatHttpMapper());
        ChatStreamTask task =
                new ChatStreamTask(
                        answerQuestion,
                        UserQuestion.of("What is the capital city of Germany?"),
                        sink);
        Thread worker = new Thread(task, "chat-stream-task-test");

        worker.start();
        assertThat(enteredUseCase.await(1, TimeUnit.SECONDS)).isTrue();
        task.cancel();
        assertThat(observedInterrupt.await(1, TimeUnit.SECONDS)).isTrue();
        worker.join(1_000);

        assertThat(worker.isAlive()).isFalse();
        assertThat(sink.terminalEventSent()).isFalse();
    }

    private static List<SseEvent> parseSseEvents(String content) {
        List<SseEvent> events = new ArrayList<>();
        String currentName = null;
        StringBuilder data = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.startsWith("event:")) {
                currentName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            } else if (line.isEmpty() && currentName != null) {
                events.add(new SseEvent(currentName, data.toString()));
                currentName = null;
                data = new StringBuilder();
            }
        }
        if (currentName != null) {
            events.add(new SseEvent(currentName, data.toString()));
        }
        return events;
    }

    private record SseEvent(String name, String data) {}
}
