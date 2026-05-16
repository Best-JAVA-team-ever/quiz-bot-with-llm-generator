package com.quizbot.core.service;

import com.quizbot.core.domain.Question;
import java.util.List;

public interface QuizService {
    Question startQuiz(Long userId, List<String> topics);
    boolean processAnswer(Long userId, String answer);
}
