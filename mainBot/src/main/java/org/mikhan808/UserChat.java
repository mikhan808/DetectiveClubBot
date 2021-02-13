package org.mikhan808;

public class UserChat {
    private Long id;
    private String name;
    private int score;
    private int status;
    private int votes;
    private boolean guessed;
    private int currentRoundScore = 0;

    public final static int ENTER_NAME=1;
    public final static int OK=2;
    public final static int ACTIVE_PLAYER = 3;
    public final static int ENTER_COUNT_PLAYERS = 4;
    public final static int CONSPIRATOR=5;
    public final static int ACTIVE_PLAYER_X = 6;
    public final static int VOTE = 7;

    public UserChat(Long id, String name) {
        this.id = id;
        this.name = name;
        score=0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getVotes() {
        return votes;
    }

    public void resetVotes()
    {
        votes = 0;
    }

    public void incVotes(){
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
}
