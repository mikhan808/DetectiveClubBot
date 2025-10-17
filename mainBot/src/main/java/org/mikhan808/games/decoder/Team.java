package org.mikhan808.games.decoder;

import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Team {
    private final List<String> cards;
    private final Map<String, List<Message>> associates;
    private final Map<String, Message> currentAssociates;
    private final List<String> resultVotes;
    private final List<String> votes;
    List<DecoderUserChat> userChats;
    int winCards = 0;
    int loseCards = 0;
    private String name;
    private int indexActivePlayer = 0;
    private boolean firstTurn = true;

    public Team() {
        userChats = new ArrayList<>();
        cards = new ArrayList<>();
        associates = new HashMap<>();
        currentAssociates = new HashMap<>();
        votes = new ArrayList<>();
        resultVotes = new ArrayList<>();
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
        associates.put(card, new ArrayList<>());
    }

    public List<String> getCards() {
        return cards;
    }

    public String getCardsToString() {
        String x = "";
        for (int i = 0; i < cards.size(); i++) x += (i + 1) + " = " + cards.get(i) + "\n";
        return x;
    }

    public Map<String, List<Message>> getAssociates() {
        return associates;
    }

    public Map<String, Message> getCurrentAssociates() {
        return currentAssociates;
    }

    public void resetVotes() {
        votes.clear();
        resultVotes.clear();
        for (int i = 1; i <= cards.size(); i++) votes.add("" + i);
    }

    public List<String> getVotes() {
        return votes;
    }

    public boolean removeVote(String vote) {
        for (int i = 0; i < votes.size(); i++) {
            if (votes.get(i).equals(vote)) {
                resultVotes.add(votes.get(i));
                votes.remove(i);
                return true;
            }
        }
        return false;
    }

    public List<String> getResultVotes() {
        return resultVotes;
    }

    public String getResultVotesToString() {
        String x = "";
        for (int i = 0; i < resultVotes.size(); i++) {
            if (i != 0) x += ", ";
            x += resultVotes.get(i);
        }
        return x;
    }

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
