package org.mikhan808.games.quiz;

import org.mikhan808.core.UserChat;

public class MultiplicationUserChat extends UserChat {
    public static final int AWAITING_ANSWER = 200;
    public static final int ENTER_ROUNDS = 201;

    private int factorA;
    private int factorB;
    private int totalAnswered;
    private int totalCorrect;
    private int totalScore;
    private boolean answeredThisRound;

    public MultiplicationUserChat(Long id, String name) {
        super(id, name);
    }

    public int getFactorA() {
        return factorA;
    }

    public void setFactorA(int factorA) {
        this.factorA = factorA;
    }

    public int getFactorB() {
        return factorB;
    }

    public void setFactorB(int factorB) {
        this.factorB = factorB;
    }

    public void incrementCorrect() {
        totalCorrect++;
    }

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public void incrementAnswered() {
        totalAnswered++;
    }

    public int getTotalAnswered() {
        return totalAnswered;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void addScore(int delta) {
        totalScore += delta;
    }

    public boolean hasAnsweredThisRound() {
        return answeredThisRound;
    }

    public void setAnsweredThisRound(boolean answeredThisRound) {
        this.answeredThisRound = answeredThisRound;
    }

    public void resetStats() {
        totalAnswered = 0;
        totalCorrect = 0;
        totalScore = 0;
    }
}
