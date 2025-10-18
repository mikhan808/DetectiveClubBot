package org.mikhan808.core;

import org.mikhan808.Bot;
import org.mikhan808.games.cardgames.decoder.DecoderUserChat;
import org.mikhan808.games.cardgames.detectiveclub.DetectiveUserChat;
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
    protected int enterNames = 0;
    protected boolean finishSetting = false;

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
                bot.findUser(user.getId()).setGame(null);
                if (players.size() >= getMinCountPlayers()) {
                    sendTextToAll(user.getName() + " покинул игру");
                    countPlayers--;
                } else {
                    sendTextToAll(user.getName() + " завершил игру");
                    finishGame();
                }
            }
            if (user.getStatus() == UserChat.ENTER_NAME) {
                enterName(msg, user);
            }else if (user.getStatus() == DetectiveUserChat.ENTER_COUNT_PLAYERS) {
                enterCountPlayers(msg, user);
            } else {
                processMsg(update);
            }
        }
    }

    protected void finishGame()
    {
        sendTextToAll("Конец игры");
        for (UserChat user : players) {
            user.setGame(null);
            bot.findUser(user.getId()).setGame(null);
        }
        bot.removeGame(this);
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
    protected abstract void afterEnterCountPlayers(UserChat user);
    protected abstract void beginGame();
    public abstract int getMinCountPlayers();
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

    protected void checkAfterEnterName(UserChat user)
    {
        Long id = user.getId();
        if (players.indexOf(user) == 0) {
            user.setStatus(UserChat.ENTER_COUNT_PLAYERS);
            bot.sendText(id, "Введите количество игроков");
        } else {
            defaultAfterEnterName(user);
        }
    }

    protected void defaultAfterEnterName(UserChat user)
    {
        user.setStatus(UserChat.OK);
        if (enterNames < countPlayers || !finishSetting)
            bot.sendText(user.getId(), "Ожидаем подключения всех игроков");
        else {
            beginGame();
        }
    }

    protected void enterName(Message msg, UserChat user) {
        Long id = user.getId();
        String name = msg.getText();
        UserChat userForName =  findUserByName(name);
        if (userForName == null || userForName == user) {
            user.setName(name);
            enterNames++;
            checkAfterEnterName(user);
        } else bot.sendText(id, "Пожалуйста введите другое имя, данное имя уже занято");
    }

    private void enterCountPlayers(Message msg, UserChat user) {
        if (msg.getText() != null)
            try {
                int x = Integer.parseInt(msg.getText().trim());
                if (x >= getMinCountPlayers()) {
                    countPlayers = x;
                    afterEnterCountPlayers(user);
                } else {
                    bot.sendText(user.getId(), "Количество игроков не может быть меньше " + getMinCountPlayers());
                }
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }


}

