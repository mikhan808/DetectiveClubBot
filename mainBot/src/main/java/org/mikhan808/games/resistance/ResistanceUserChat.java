package org.mikhan808.games.resistance;

import org.mikhan808.core.UserChat;

public class ResistanceUserChat extends UserChat {
    public static final int TEAM_SELECTION = 20;
    public static final int TEAM_VOTE = 21;
    public static final int MISSION_VOTE = 22;

    private boolean spy;

    public ResistanceUserChat(Long id, String name) {
        super(id, name);
    }

    public boolean isSpy() {
        return spy;
    }

    public void setSpy(boolean spy) {
        this.spy = spy;
    }
}

