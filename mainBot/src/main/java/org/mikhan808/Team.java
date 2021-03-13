package org.mikhan808;

import java.util.ArrayList;
import java.util.List;

public class Team {
    List<UserChat> userChats;
    int winCards = 0;
    int loseCards = 0;
    private String name;
    private final int indexActivePlayer = 0;
    private final int[] code;
    private final int size_code;
    private final Bot bot;
    private List<String> cards;
    private int size_cards = 4;

    public Team(int size_cards, Bot bot) {
        this.bot = bot;
        this.size_cards = size_cards;
        size_code = size_cards - 1;
        code = new int[size_code];
        userChats = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UserChat getActivePlayer() {
        return userChats.get(indexActivePlayer);
    }

    public void addPlayer(UserChat user) {
        userChats.add(user);
        if (userChats.indexOf(user) == 0) {
            while (user.getStatus() != UserChat.OK) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
