package com.quizbot.api.dispatcher;

import com.quizbot.core.service.QuizService;
import org.springframework.stereotype.Component;

@Component
public class MessageDispatcher {
    private final QuizService quizService;

    public MessageDispatcher(QuizService quizService) {
        this.quizService = quizService;
    }

    public String handleCommand(Long userId, String command) {
        // Here will be the logic to route to Admin or User modules
        return "Received: " + command;
    }
}
