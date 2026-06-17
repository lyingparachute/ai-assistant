package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.orchestration.AnswerQuestionUseCase;
import dev.localassistant.assistant.question.AnswerSource;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.tools.CountryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerIntegrationTest {

    @Mock
    private AnswerQuestionUseCase answerQuestionUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ChatController(answerQuestionUseCase))
                        .setControllerAdvice(new HttpExceptionHandler())
                        .setMessageConverters(new MappingJackson2HttpMessageConverter())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void happyPathReturnsStructuredAnswer() throws Exception {
        when(answerQuestionUseCase.answer(any(UserQuestion.class)))
                .thenReturn(
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
                                        "trace-123")));

        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"What is the capital city of Germany?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerText").value("The capital of Germany is Berlin."))
                .andExpect(jsonPath("$.traceCorrelationId").value("trace-123"))
                .andExpect(jsonPath("$.sources[0].type").value("countries_facts"))
                .andExpect(jsonPath("$.sources[0].status").value("USED"))
                .andExpect(jsonPath("$.sources[0].countryInfo.capital").value("Berlin"));
    }

    @Test
    void blankQuestionReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.message").value("question must not be blank"));
    }

    @Test
    void sourceUnavailableReturnsOkWithStructuredBody() throws Exception {
        when(answerQuestionUseCase.answer(any(UserQuestion.class)))
                .thenReturn(
                        new ConversationTurn(
                                UserQuestion.of("What is the temperature currently in Munich?"),
                                AssistantAnswer.withTrace(
                                        "Weather for Munich is unavailable: weather service down.",
                                        List.of(
                                                AnswerSource.WeatherObservation.unavailable(
                                                        "weather service down", "retry later")),
                                        "trace-weather")));

        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"What is the temperature currently in Munich?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerText").value("Weather for Munich is unavailable: weather service down."))
                .andExpect(jsonPath("$.sources[0].type").value("weather_observation"))
                .andExpect(jsonPath("$.sources[0].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.sources[0].unavailableMessage").value("weather service down"));
    }
}
