package org.mikhan808;

import java.util.ArrayList;
import java.util.List;

public class UserChat {
    private Long id;
    private String name;
    private int score;
    private int status;
    private int votes;
    private boolean guessed;
    private int currentRoundScore = 0;
    private final List<String> cards;
    private final List<String> table;
    public final static int ACTIVE_PLAYER_Z = 8;


    public final static int ENTER_NAME = 1;
    public final static int OK = 2;
    public final static int ACTIVE_PLAYER = 3;
    public final static int ENTER_COUNT_PLAYERS = 4;
    public final static int CONSPIRATOR = 5; // Detective Club
    // Decoder-specific statuses
    public final static int DISCUSSION = 5; // Decoder
    public final static int ACTIVE_PLAYER_X = 6;
    public final static int VOTE = 7;
    public final static int VOTE_X = 9;
    public final static int ENTER_TYPE_GAME = 10;
    public final static int ENTER_COUNT_TEAMS = 10; // Decoder
    public final static int JOIN_GAME = 11;
    public final static int ENTER_COUNT_ROUNDS = 12;
    public final static int ENTER_COUNT_CARDS = 13;
    public final static int SELECT_GAME_TYPE = 99;
    public final static int ENTER_NAME_TEAM = 14; // Decoder
    // Resistance-specific statuses
    public final static int TEAM_SELECTION = 20;
    public final static int TEAM_VOTE = 21;
    public final static int MISSION_VOTE = 22;
    public int indexCode = 0; // Decoder
    private GameSession game;
    private Team team; // Decoder

    private UserChat voteUser;
    private int deductedPoints;

    public UserChat(Long id, String name) {
        this.id = id;
        this.name = name;
        score = 0;
        cards = new ArrayList<>();
        table = new ArrayList<>();
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

    public UserChat getVoteUser() {
        return voteUser;
    }

    public void setVoteUser(UserChat voteUser) {
        this.voteUser = voteUser;
    }

    public int getDeductedPoints() {
        return deductedPoints;
    }

    public void setDeductedPoints(int deductedPoints) {
        this.deductedPoints = deductedPoints;
        score -= deductedPoints;
    }

    public GameSession getGame() {
        return game;
    }

    public void setGame(GameSession game) {
        this.game = game;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}
