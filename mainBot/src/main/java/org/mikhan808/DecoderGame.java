package org.mikhan808;

import org.telegram.telegrambots.meta.api.objects.Update;

public class DecoderGame implements GameSession {
    private final Bot bot;
    private int id;

    public DecoderGame(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void readMsg(Update update) {
        Long chatId = update.getMessage().getChatId();
        bot.sendText(chatId, "Decoder: логика игры будет добавлена позже. Для выхода отправьте /exit");
        if (update.getMessage().hasText() && "/exit".equals(update.getMessage().getText())) {
            // find user and detach
            UserChat user = bot.findUser(chatId);
            if (user != null) {
                user.setGame(null);
            }
            bot.removeGame(this);
            bot.sendText(chatId, "Вы вышли из игры Decoder");
        }
    }

    @Override
    public boolean addPlayer(UserChat user) {
        user.setGame(this);
        bot.sendText(user.getId(), "Decoder: игра ещё не реализована в этой сборке");
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

