package org.mikhan808.games.cardgames;

import org.mikhan808.Bot;
import org.mikhan808.core.Game;
import org.mikhan808.games.cardgames.detectiveclub.DetectiveClubGame;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Stack;

public abstract class AbstractCardsGame extends Game {
    public static final String WORDS_RESOURCE_NAME = "Words.csv";
    private Stack<String> cards;
    protected AbstractCardsGame(Bot bot)
    {
        super(bot);
        cards = getCards();
        Collections.shuffle(cards, rand);
    }
    Stack<String> getCards() {
        Stack<String> result = new Stack<>();
        try {
            InputStream is = DetectiveClubGame.class.getClassLoader().getResourceAsStream(WORDS_RESOURCE_NAME);
            if (is == null) {
                throw new IOException("Resource not found: " + WORDS_RESOURCE_NAME);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    result.push(line);
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    protected String getNextCard() {
        if (cards.empty()) {
            cards = getCards();
            Collections.shuffle(cards, rand);
        }
        return cards.pop();
    }
}
