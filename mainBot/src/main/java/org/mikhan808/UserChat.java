package org.mikhan808;

import java.util.List;

public class UserChat {
    private Long id;
    private String name;
    private int status;
    private boolean guessed;
    public final static int DISCUSSION = 5;
    public final static int ACTIVE_PLAYER_Z = 8;


    public final static int ENTER_NAME = 1;
    public final static int OK = 2;
    public final static int ACTIVE_PLAYER = 3;
    public final static int ENTER_COUNT_PLAYERS = 4;
    int indexCode = 0;
    public final static int ACTIVE_PLAYER_X = 6;
    public final static int VOTE = 7;
    public final static int VOTE_X = 9;
    public final static int ENTER_COUNT_TEAMS = 10;
    public final static int JOIN_GAME = 11;
    public final static int ENTER_COUNT_ROUNDS = 12;
    public final static int ENTER_COUNT_CARDS = 13;
    public final static int ENTER_NAME_TEAM = 14;
    private Game game;
    private Team team;

    public UserChat(Long id, String name) {
        this.id = id;
        this.name = name;
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

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isGuessed() {
        return guessed;
    }

    public void setGuessed(boolean guessed) {
        this.guessed = guessed;
    }

    public List<String> getCards() {
        return team.getCards();
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}

