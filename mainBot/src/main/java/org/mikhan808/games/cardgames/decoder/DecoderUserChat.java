package org.mikhan808.games.cardgames.decoder;

import org.mikhan808.core.UserChat;

public class DecoderUserChat extends UserChat {
    public static final int DISCUSSION = 5;
    public static final int ACTIVE_PLAYER_X = 6;
    public static final int ENTER_COUNT_TEAMS = 10;
    public static final int ENTER_COUNT_CARDS = 13;
    public static final int ENTER_NAME_TEAM = 14;
    public static final int ENTER_CODE_LENGTH = 15;
    public static final int GUESS_OWN_CODE = 16;
    public static final int INTERCEPT_CODE = 17;

    int indexCode = -1;
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
