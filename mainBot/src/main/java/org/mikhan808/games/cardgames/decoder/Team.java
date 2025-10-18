package org.mikhan808.games.cardgames.decoder;

import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Team {
    private final List<String> cards;
    private final Map<String, List<Message>> associatesHistory;
    private final Map<Integer, Message> currentAssociates;
    private final List<DecoderUserChat> userChats;
    private int winCards = 0;
    private int loseCards = 0;
    private String name;
    private int indexActivePlayer = 0;
    private boolean firstTurn = true;
    private int[] ownGuess;
    private int[] interceptGuess;

    public Team() {
        userChats = new ArrayList<>();
        cards = new ArrayList<>();
        associatesHistory = new HashMap<>();
        currentAssociates = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DecoderUserChat getActivePlayer() {
        return userChats.get(indexActivePlayer);
    }

    public void addPlayer(DecoderUserChat user) {
        userChats.add(user);
        user.setTeam(this);
    }

    public void addCard(String card) {
        cards.add(card);
        associatesHistory.put(card, new ArrayList<>());
    }

    public List<String> getCards() {
        return cards;
    }

    public String getCardsToString() {
        String x = "";
        for (int i = 0; i < cards.size(); i++) x += (i + 1) + " = " + cards.get(i) + "\n";
        return x;
    }

    public Map<String, List<Message>> getAssociatesHistory() {
        return associatesHistory;
    }

    public Map<Integer, Message> getCurrentAssociates() {
        return currentAssociates;
    }

    public void resetRoundState() {
        ownGuess = null;
        interceptGuess = null;
    }

    public boolean hasOwnGuess() { return ownGuess != null; }

    public boolean hasInterceptGuess() { return interceptGuess != null; }

    public void setOwnGuess(int[] guess) {
        this.ownGuess = guess;
    }

    public int[] getOwnGuess() {
        return ownGuess;
    }

    public void setInterceptGuess(int[] guess) {
        this.interceptGuess = guess;
    }

    public int[] getInterceptGuess() {
        return interceptGuess;
    }

    public void incrementWinCards() {
        winCards++;
    }

    public void incrementLoseCards() {
        loseCards++;
    }

    public int getWinCards() { return winCards; }

    public int getLoseCards() { return loseCards; }

    public void incIndexActivePlayer() {
        indexActivePlayer++;
        if (indexActivePlayer >= userChats.size()) indexActivePlayer = 0;
    }

    public boolean isFirstTurn() {
        return firstTurn;
    }

    public void setFirstTurn(boolean firstTurn) {
        this.firstTurn = firstTurn;
    }

    public List<DecoderUserChat> getPlayers() {
        return userChats;
    }
}
