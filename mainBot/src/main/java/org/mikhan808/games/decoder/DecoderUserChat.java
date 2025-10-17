package org.mikhan808.games.decoder;

import org.mikhan808.core.UserChat;

public class DecoderUserChat extends UserChat {
    public static final int ENTER_NAME = 1;
    public static final int OK = 2;
    public static final int ACTIVE_PLAYER = 3;
    public static final int ENTER_COUNT_PLAYERS = 4;
    public static final int DISCUSSION = 5;
    public static final int ACTIVE_PLAYER_X = 6;
    public static final int VOTE = 7;
    public static final int ACTIVE_PLAYER_Z = 8;
    public static final int VOTE_X = 9;
    public static final int ENTER_COUNT_TEAMS = 10;
    public static final int JOIN_GAME = 11;
    public static final int ENTER_COUNT_ROUNDS = 12;
    public static final int ENTER_COUNT_CARDS = 13;
    public static final int ENTER_NAME_TEAM = 14;

    int indexCode = 0;
    private Team team;

    public DecoderUserChat(Long id, String name) {
        super(id, name);
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}

