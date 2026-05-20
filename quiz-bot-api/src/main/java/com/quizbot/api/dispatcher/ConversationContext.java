package com.quizbot.api.dispatcher;

import com.quizbot.core.domain.Question;
import java.util.ArrayList;
import java.util.List;

public class ConversationContext {
    private UserState state = UserState.IDLE;
    private List<String> pendingTopics = new ArrayList<>();
    private String questionText;
    private String correctAnswer;
    private List<String> incorrectAnswers = new ArrayList<>();
    private Integer difficulty;
    private String targetId; // For updates/deletes
    private String targetGroupId; // For group actions
    private String deleteScope; // 'id', 'topic', 'all'
    private String deleteValue; // topic name or id
    private int updateFieldIndex = 0; // 0: text, 1: correct, 2: incorrect, 3: difficulty, 4: explanation
    private Question pendingQuestion;
    private Question activeQuestion;
    private List<String> currentOptions = new ArrayList<>();

    public UserState getState() { return state; }
    public void setState(UserState state) { this.state = state; }

    public List<String> getPendingTopics() { return pendingTopics; }
    public void setPendingTopics(List<String> pendingTopics) { this.pendingTopics = pendingTopics; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public List<String> getIncorrectAnswers() { return incorrectAnswers; }
    
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getTargetGroupId() { return targetGroupId; }
    public void setTargetGroupId(String targetGroupId) { this.targetGroupId = targetGroupId; }

    public String getDeleteScope() { return deleteScope; }
    public void setDeleteScope(String deleteScope) { this.deleteScope = deleteScope; }

    public String getDeleteValue() { return deleteValue; }
    public void setDeleteValue(String deleteValue) { this.deleteValue = deleteValue; }

    public int getUpdateFieldIndex() { return updateFieldIndex; }
    public void setUpdateFieldIndex(int updateFieldIndex) { this.updateFieldIndex = updateFieldIndex; }

    public Question getPendingQuestion() { return pendingQuestion; }
    public void setPendingQuestion(Question pendingQuestion) { this.pendingQuestion = pendingQuestion; }

    public Question getActiveQuestion() { return activeQuestion; }
    public void setActiveQuestion(Question activeQuestion) { this.activeQuestion = activeQuestion; }

    public List<String> getCurrentOptions() { return currentOptions; }
    public void setCurrentOptions(List<String> currentOptions) { this.currentOptions = currentOptions; }

    public void reset() {
        state = UserState.IDLE;
        pendingTopics.clear();
        questionText = null;
        correctAnswer = null;
        incorrectAnswers.clear();
        difficulty = null;
        targetId = null;
        targetGroupId = null;
        deleteScope = null;
        deleteValue = null;
        updateFieldIndex = 0;
        pendingQuestion = null;
        activeQuestion = null;
        currentOptions.clear();
    }
}
