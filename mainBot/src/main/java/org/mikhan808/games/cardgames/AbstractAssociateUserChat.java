package org.mikhan808.games.cardgames;

import org.mikhan808.core.UserChat;
import org.mikhan808.games.cardgames.detectiveclub.DetectiveUserChat;

import java.util.ArrayList;
import java.util.List;

public class AbstractAssociateUserChat extends UserChat {

    public static final int ACTIVE_PLAYER_X = 6;
    public static final int VOTE = 7;
    public static final int ACTIVE_PLAYER_Z = 8;
    public static final int VOTE_X = 9;
    public static final int ENTER_COUNT_ROUNDS = 12;
    public static final int ENTER_COUNT_CARDS = 13;

    protected int score;
    protected int votes;
    protected boolean guessed;
    protected int currentRoundScore = 0;
    protected final List<String> cards;
    protected AbstractAssociateUserChat voteUser;
    protected AbstractAssociateUserChat(Long id, String name) {
        super(id, name);
        score = 0;
        cards = new ArrayList<>();
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

    public AbstractAssociateUserChat getVoteUser() {
        return voteUser;
    }

    public void setVoteUser(AbstractAssociateUserChat voteUser) {
        this.voteUser = voteUser;
    }

}
