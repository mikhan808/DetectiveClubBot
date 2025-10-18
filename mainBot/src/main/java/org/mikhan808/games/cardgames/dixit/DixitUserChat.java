package org.mikhan808.games.cardgames.dixit;

import org.mikhan808.games.cardgames.AbstractAssociateUserChat;

public class DixitUserChat extends AbstractAssociateUserChat {
    private String cardOnTable;
    private int addingScore;

    public DixitUserChat(Long id, String name) {
        super(id,name);
    }

    public int getAddingScore() {
        return addingScore;
    }

    public void setAddingScore(int addingScore) {
        this.addingScore = addingScore;
        score += addingScore;
    }

    public String getCardOnTable() {
        return cardOnTable;
    }

    public void setCardOnTable(String cardOnTable) {
        this.cardOnTable = cardOnTable;
    }
    public void moveCardToTable(String card) {
        cards.remove(card);
        cardOnTable = card;
    }

}
