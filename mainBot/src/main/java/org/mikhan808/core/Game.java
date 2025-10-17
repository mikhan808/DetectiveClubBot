package org.mikhan808.core;

import org.mikhan808.Bot;

public abstract class Game implements GameSession {
    protected final Bot bot;
    private int id;

    protected Game(Bot bot) {
        this.bot = bot;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }
}

