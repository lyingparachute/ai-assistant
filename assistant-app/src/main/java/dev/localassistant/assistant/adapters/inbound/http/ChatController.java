package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.orchestration.AnswerQuestionUseCase;
import dev.localassistant.assistant.question.ConversationTurn;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@ConditionalOnBean(AnswerQuestionUseCase.class)
public class ChatController {

    private final AnswerQuestionUseCase answerQuestionUseCase;
    private final ChatHttpMapper chatHttpMapper;

    ChatController(AnswerQuestionUseCase answerQuestionUseCase) {
        this.answerQuestionUseCase = answerQuestionUseCase;
        this.chatHttpMapper = new ChatHttpMapper();
    }

    @PostMapping("/chat")
    ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        ConversationTurn turn = answerQuestionUseCase.answer(chatHttpMapper.toUserQuestion(request));
        return chatHttpMapper.toChatResponse(turn);
    }
}
