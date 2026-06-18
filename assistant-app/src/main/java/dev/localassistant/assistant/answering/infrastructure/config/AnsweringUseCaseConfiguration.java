package dev.localassistant.assistant.answering.infrastructure.config;

import dev.localassistant.assistant.answering.domain.AnswerQuestionUseCase;
import dev.localassistant.assistant.answering.domain.port.inbound.AnswerQuestion;
import dev.localassistant.assistant.countryfacts.domain.port.inbound.ResolveCountryFacts;
import dev.localassistant.assistant.rag.domain.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import dev.localassistant.assistant.rag.infrastructure.config.AssistantRagRetrievalProperties;
import dev.localassistant.assistant.synthesis.domain.port.outbound.LlmPort;
import dev.localassistant.assistant.weather.domain.port.inbound.ResolveWeatherObservation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!ingest-rag")
@EnableConfigurationProperties(AssistantRagRetrievalProperties.class)
class AnsweringUseCaseConfiguration {

    @Bean
    RagRetrievalPolicy ragRetrievalPolicy(final AssistantRagRetrievalProperties retrievalProperties) {
        return new RagRetrievalPolicy(
            retrievalProperties.topK(), retrievalProperties.relevanceThreshold());
    }

    @Bean
    @ConditionalOnMissingBean(AnswerQuestion.class)
    AnswerQuestion answerQuestion(
        final ResolveCountryFacts resolveCountryFacts,
        final ResolveWeatherObservation resolveWeatherObservation,
        final RetrieveRagKnowledge retrieveRagKnowledge,
        final LlmPort llmPort,
        final RagRetrievalPolicy ragRetrievalPolicy) {
        return new AnswerQuestionUseCase(
            resolveCountryFacts,
            resolveWeatherObservation,
            retrieveRagKnowledge,
            llmPort,
            ragRetrievalPolicy);
    }
}
