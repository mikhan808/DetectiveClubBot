package org.mikhan808.games.cardgames.detectiveclub;

import org.mikhan808.core.UserChat;
import org.mikhan808.games.cardgames.AbstractAssociateUserChat;

import java.util.ArrayList;
import java.util.List;

public class DetectiveUserChat extends AbstractAssociateUserChat {

    public static final int CONSPIRATOR = 5;
    public static final int ENTER_TYPE_GAME = 10;
    private final List<String> table;

    private int deductedPoints;

    public DetectiveUserChat(Long id, String name) {
        super(id, name);
        table = new ArrayList<>();
    }



    public void moveCardToTable(String card) {
        cards.remove(card);
        table.add(card);
    }

    public void resetTable() {
        table.clear();
    }

    public List<String> getTable() {
        return table;
    }

    public int getDeductedPoints() {
        return deductedPoints;
    }

    public void setDeductedPoints(int deductedPoints) {
        this.deductedPoints = deductedPoints;
        score -= deductedPoints;
    }
}

