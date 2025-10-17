package org.mikhan808.core;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface GameSession {
    void readMsg(Update update);

    boolean addPlayer(UserChat user);

    int getId();

    void setId(int id);
}

