package com.quizbot.core.service;

import com.quizbot.core.domain.Answers;
import com.quizbot.core.domain.Question;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {


    private static Question question(String id, int difficulty) {
        Question q = Question.create("text", "ans", List.of(), difficulty, "", "", List.of());
        return new Question(id, q.text(), q.correctAnswer(), q.wrongAnswers(),
                q.difficulty(), q.explanation(), q.hint(), q.topicNames(),
                q.createdAt(), q.updatedAt(), null);
    }

    @Test
    void pickNext_noQuestions_returnsEmpty() {
        assertEquals(Optional.empty(), QuizServiceImpl.pickNext(List.of(), List.of()));
    }

    @Test
    void pickNext_questionCorrectlyAnswered_returnsEmpty() {
        Question q = question("q1", 1);
        Answers correct = Answers.create("u", "q1", "ans", true);
        assertEquals(Optional.empty(), QuizServiceImpl.pickNext(List.of(q), List.of(correct)));
    }

    @Test
    void pickNext_unansweredQuestion_returnsIt() {
        Question q = question("q1", 1);
        Optional<Question> result = QuizServiceImpl.pickNext(List.of(q), List.of());
        assertTrue(result.isPresent());
        assertEquals("q1", result.get().id());
    }

    @Test
    void pickNext_incorrectAnswer_doesNotExcludeQuestion() {
        Question q = question("q1", 1);
        Answers wrong = Answers.create("u", "q1", "bad", false);
        assertTrue(QuizServiceImpl.pickNext(List.of(q), List.of(wrong)).isPresent());
    }


    @Test
    void calculateStats_emptyList_returnsZeros() {
        Map<String, Object> s = StatisticsServiceImpl.calculateStats(List.of());
        assertEquals(0L, s.get("total"));
        assertEquals(0L, s.get("correct"));
        assertEquals(0L, s.get("incorrect"));
        assertEquals(0.0, s.get("percentage"));
    }

    @Test
    void calculateStats_allCorrect_returns100Percent() {
        List<Answers> answers = List.of(
                Answers.create("u", "q1", "A", true),
                Answers.create("u", "q2", "B", true));
        Map<String, Object> s = StatisticsServiceImpl.calculateStats(answers);
        assertEquals(2L, s.get("total"));
        assertEquals(2L, s.get("correct"));
        assertEquals(100.0, s.get("percentage"));
    }

    @Test
    void calculateStats_mixed_returnsCorrectCounts() {
        List<Answers> answers = List.of(
                Answers.create("u", "q1", "A", true),
                Answers.create("u", "q2", "B", false),
                Answers.create("u", "q3", "C", false));
        Map<String, Object> s = StatisticsServiceImpl.calculateStats(answers);
        assertEquals(3L, s.get("total"));
        assertEquals(1L, s.get("correct"));
        assertEquals(2L, s.get("incorrect"));
        assertEquals(33.3, s.get("percentage"));
    }


    private final TopicServiceImpl topicService = new TopicServiceImpl(null, null, null);

    @Test
    void isValid_nullAndReservedKeyword_returnsFalse() {
        assertFalse(topicService.isValid(null));
        assertFalse(topicService.isValid("all"));
        assertFalse(topicService.isValid("ALL"));
    }

    @Test
    void isValid_tooShortOrTooLong_returnsFalse() {
        assertFalse(topicService.isValid("a"));
        assertFalse(topicService.isValid("a".repeat(33)));
    }

    @Test
    void isValid_correctNames_returnsTrue() {
        assertTrue(topicService.isValid("java"));
        assertTrue(topicService.isValid("Spring Boot"));
        assertTrue(topicService.isValid("алгоритмы"));
    }


    private final GroupServiceImpl groupService = new GroupServiceImpl(null, null, null);

    @Test
    void generateInviteLink_returnsCorrectFormat() {
        assertEquals("https://t.me/quiz_bot?start=join_abc123",
                groupService.generateInviteLink("abc123"));
    }
}
