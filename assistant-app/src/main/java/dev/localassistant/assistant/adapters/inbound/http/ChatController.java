package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.orchestration.AnswerQuestionUseCase;
import dev.localassistant.assistant.question.ConversationTurn;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AnswerQuestionUseCase answerQuestionUseCase;
    private final ChatHttpMapper chatHttpMapper;

    public ChatController(AnswerQuestionUseCase answerQuestionUseCase, ChatHttpMapper chatHttpMapper) {
        this.answerQuestionUseCase = answerQuestionUseCase;
        this.chatHttpMapper = chatHttpMapper;
    }

    @PostMapping("/chat")
    ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        ConversationTurn turn = answerQuestionUseCase.answer(chatHttpMapper.toUserQuestion(request));
        return chatHttpMapper.toChatResponse(turn);
    }
}
