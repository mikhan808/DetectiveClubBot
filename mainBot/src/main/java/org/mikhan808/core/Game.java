package org.mikhan808.core;

import org.mikhan808.Bot;
import org.mikhan808.games.detectiveclub.DetectiveUserChat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class Game implements GameSession {
    protected final Bot bot;
    private int id;
    protected List<UserChat> players;
    protected Random rand;
    protected int countPlayers = 1000;

    protected Game(Bot bot) {
        this.bot = bot;
        this.players = new ArrayList<>();
        rand = new Random();
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    public void readMsg(Update update)
    {
        Message msg = update.getMessage();
        Long id = msg.getChatId();
        UserChat user = findUser(id);
        if (user != null) {
            if (msg.hasText() && msg.getText().equals("/exit")) {
                players.remove(user);
                user.setGame(null);
                if (players.size() >= getMinCountPlayers()) {
                    sendTextToAll(user.getName() + " покинул игру");
                    countPlayers--;
                } else {
                    sendTextToAll(user.getName() + " завершил игру");
                    finishGame();
                }
            }
            else {
                processMsg(update);
            }
        }
    }

    public abstract void processMsg(Update update);

    protected UserChat findUser(Long id) {
        for (UserChat user : players) {
            if (user.getId().equals(id))
                return user;
        }
        return null;
    }

    protected UserChat findUserByName(String name) {
        for (UserChat user : players) {
            if (user.getName() != null && user.getName().equalsIgnoreCase(name))
                return user;
        }
        return null;
    }

    protected void sendTextToAll(String text) {
        for (UserChat user : players) {
            bot.sendText(user.getId(), text);
        }
    }

    protected void copyMsgToAll(Message message) { for (UserChat u : players) bot.copyMessage(u.getId(), message); }

    protected void forwardMsgToList(Message message, List<? extends UserChat> playerList)
    {
        forwardMsgToList(message,playerList,false);
    }

    protected void forwardMsgToList(Message message, List<? extends UserChat> playerList, boolean sendToMe)
    {
        for (UserChat u : playerList)
            if(sendToMe||!message.getChatId().equals(u.getId()))
                bot.forwardMessage(u.getId(), message);
    }

    protected abstract UserChat createUserChat(UserChat lobbyUserChat);

    @Override
    public boolean addPlayer(UserChat lobbyUser) {
        if (players.size() < countPlayers) {
            lobbyUser.setGame(this);
            UserChat u = createUserChat(lobbyUser);
            u.setStatus(UserChat.ENTER_NAME);
            players.add(u);
            bot.sendText(lobbyUser.getId(), "Введите имя");
            return true;
        } else {
            bot.sendText(lobbyUser.getId(), "Слишком много игроков. Вернитесь в меню.");
            return false;
        }
    }

    public abstract int getMinCountPlayers();
    public abstract void finishGame();
}

