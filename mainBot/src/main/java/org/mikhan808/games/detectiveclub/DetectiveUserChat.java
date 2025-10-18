package org.mikhan808.games.detectiveclub;

import org.mikhan808.core.UserChat;

import java.util.ArrayList;
import java.util.List;

public class DetectiveUserChat extends UserChat {
    public static final int ACTIVE_PLAYER = 3;
    public static final int ENTER_COUNT_PLAYERS = 4;
    public static final int CONSPIRATOR = 5;
    public static final int ACTIVE_PLAYER_X = 6;
    public static final int VOTE = 7;
    public static final int ACTIVE_PLAYER_Z = 8;
    public static final int VOTE_X = 9;
    public static final int ENTER_TYPE_GAME = 10;
    public static final int ENTER_COUNT_ROUNDS = 12;
    public static final int ENTER_COUNT_CARDS = 13;
    private final List<String> cards;
    private final List<String> table;
    private int score;
    private int status;
    private int votes;
    private boolean guessed;
    private int currentRoundScore = 0;
    private DetectiveUserChat voteUser;
    private int deductedPoints;

    public DetectiveUserChat(Long id, String name) {
        super(id, name);
        score = 0;
        cards = new ArrayList<>();
        table = new ArrayList<>();
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getVotes() {
        return votes;
    }

    public void resetVotes() {
        votes = 0;
    }

    public void incVotes() {
        votes++;
    }

    public boolean isGuessed() {
        return guessed;
    }

    public void setGuessed(boolean guessed) {
        this.guessed = guessed;
    }

    public int getCurrentRoundScore() {
        return currentRoundScore;
    }

    public void setCurrentRoundScore(int currentRoundScore) {
        this.currentRoundScore = currentRoundScore;
        score += currentRoundScore;
    }

    public List<String> getCards() {
        return cards;
    }

    public void addCard(String card) {
        cards.add(card);
    }

    public void moveCardToTable(String card) {
        cards.remove(card);
        table.add(card);
    }

    public void resetTable() {
        table.clear();
    }

    public List<String> getTable() {
        return table;
    }

    public DetectiveUserChat getVoteUser() {
        return voteUser;
    }

    public void setVoteUser(DetectiveUserChat voteUser) {
        this.voteUser = voteUser;
    }

    public int getDeductedPoints() {
        return deductedPoints;
    }

    public void setDeductedPoints(int deductedPoints) {
        this.deductedPoints = deductedPoints;
        score -= deductedPoints;
    }
}

