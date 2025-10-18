package org.mikhan808.games.cardgames;

import org.mikhan808.Bot;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.List;

import static org.mikhan808.Bot.NO;
import static org.mikhan808.Bot.YES;

public abstract class AbstractAssociateCardGame extends AbstractCardsGame{
    protected int count_card_on_round;
    protected int active_player_score = 3;
    protected int guessed_score = 3;
    protected int count_cards_on_hands = 6;
    protected int countVotePlayers = 0;
    protected int indexActivePlayer = 0;
    protected int countRounds = 2;
    protected Message associate;
    protected final List<String> yesno;
    protected AbstractAssociateCardGame(Bot bot) {
        super(bot);
        yesno = new ArrayList<>();
        yesno.add(YES);
        yesno.add(NO);
        count_card_on_round = 1;
    }
}
