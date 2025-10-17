package org.mikhan808;

import org.telegram.telegrambots.meta.api.objects.Update;

public class ResistanceGame implements GameSession {
    private final Bot bot;
    private int id;

    public ResistanceGame(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void readMsg(Update update) {
        Long chatId = update.getMessage().getChatId();
        bot.sendText(chatId, "Сопротивление: логика игры будет добавлена позже. Для выхода отправьте /exit");
        if (update.getMessage().hasText() && "/exit".equals(update.getMessage().getText())) {
            UserChat user = bot.findUser(chatId);
            if (user != null) {
                user.setGame(null);
            }
            bot.removeGame(this);
            bot.sendText(chatId, "Вы вышли из игры Сопротивление");
        }
    }

    @Override
    public boolean addPlayer(UserChat user) {
        user.setGame(this);
        bot.sendText(user.getId(), "Сопротивление: игра ещё не реализована в этой сборке");
        return true;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }
}

