package dev.localassistant.assistant.answering.api.http;

import dev.localassistant.assistant.answering.domain.port.inbound.AnswerQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
class CorsPolicyTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4321";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AnswerQuestion answerQuestion;

    @Test
    void preflightForPostIsAllowedWithPostAndOptionsButNotGet() throws Exception {
        mockMvc.perform(
                        options("/api/chat")
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                                        containsString("POST")))
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                                        containsString("OPTIONS")))
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                                        not(containsString("GET"))));
    }

    @Test
    void preflightRequestingGetIsRejected() throws Exception {
        mockMvc.perform(
                        options("/api/chat")
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties({AssistantCorsProperties.class, AssistantChatProperties.class})
    @Import({HttpInboundConfiguration.class, ChatHttpMapper.class})
    static class TestSlice {}
}
