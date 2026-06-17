package dev.localassistant.assistant.config;

import dev.localassistant.assistant.llm.LlmPort;
import dev.localassistant.assistant.orchestration.AnswerQuestionUseCase;
import dev.localassistant.assistant.orchestration.ResponseComposer;
import dev.localassistant.assistant.orchestration.SourceRoutingPolicy;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.tools.CountriesPort;
import dev.localassistant.assistant.tools.WeatherPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OrchestrationConfiguration {

    @Bean
    SourceRoutingPolicy sourceRoutingPolicy() {
        return new SourceRoutingPolicy();
    }

    @Bean
    ResponseComposer responseComposer() {
        return new ResponseComposer();
    }

    @Bean
    RagRetrievalPolicy ragRetrievalPolicy(AssistantRagProperties assistantRagProperties) {
        return new RagRetrievalPolicy(
                assistantRagProperties.topK(), assistantRagProperties.relevanceThreshold());
    }

    @Bean
    @ConditionalOnBean({CountriesPort.class, WeatherPort.class, RagKnowledgePort.class, LlmPort.class})
    AnswerQuestionUseCase answerQuestionUseCase(
            CountriesPort countriesPort,
            WeatherPort weatherPort,
            RagKnowledgePort ragKnowledgePort,
            LlmPort llmPort,
            SourceRoutingPolicy sourceRoutingPolicy,
            ResponseComposer responseComposer,
            RagRetrievalPolicy ragRetrievalPolicy) {
        return new AnswerQuestionUseCase(
                countriesPort,
                weatherPort,
                ragKnowledgePort,
                llmPort,
                sourceRoutingPolicy,
                responseComposer,
                ragRetrievalPolicy);
    }
}
