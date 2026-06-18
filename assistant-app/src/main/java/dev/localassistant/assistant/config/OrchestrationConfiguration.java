package dev.localassistant.assistant.config;

import dev.localassistant.assistant.countryfacts.domain.port.inbound.ResolveCountryFacts;
import dev.localassistant.assistant.synthesis.domain.port.outbound.LlmPort;
import dev.localassistant.assistant.orchestration.AnswerQuestionUseCase;
import dev.localassistant.assistant.orchestration.ResponseComposer;
import dev.localassistant.assistant.orchestration.SourceRoutingPolicy;
import dev.localassistant.assistant.rag.domain.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import dev.localassistant.assistant.weather.domain.port.inbound.ResolveWeatherObservation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!ingest-rag")
public class OrchestrationConfiguration {

    @Bean
    SourceRoutingPolicy sourceRoutingPolicy() {
        return new SourceRoutingPolicy();
    }

    @Bean
    ResponseComposer responseComposer() {
        return new ResponseComposer();
    }

    @Bean
    RagRetrievalPolicy ragRetrievalPolicy(AssistantRagRetrievalProperties retrievalProperties) {
        return new RagRetrievalPolicy(
                retrievalProperties.topK(), retrievalProperties.relevanceThreshold());
    }

    @Bean
    @ConditionalOnBean({ResolveCountryFacts.class, ResolveWeatherObservation.class, RetrieveRagKnowledge.class, LlmPort.class})
    AnswerQuestionUseCase answerQuestionUseCase(
            ResolveCountryFacts resolveCountryFacts,
            ResolveWeatherObservation resolveWeatherObservation,
            RetrieveRagKnowledge retrieveRagKnowledge,
            LlmPort llmPort,
            SourceRoutingPolicy sourceRoutingPolicy,
            ResponseComposer responseComposer,
            RagRetrievalPolicy ragRetrievalPolicy) {
        return new AnswerQuestionUseCase(
                resolveCountryFacts,
                resolveWeatherObservation,
                retrieveRagKnowledge,
                llmPort,
                sourceRoutingPolicy,
                responseComposer,
                ragRetrievalPolicy);
    }
}
