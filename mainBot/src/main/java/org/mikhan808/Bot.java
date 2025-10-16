package org.mikhan808;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Bot extends TelegramLongPollingBot {

    public static final String YES = "Да";
    public static final String NO = "Нет";
    public static final String CREATE_GAME = "Создать игру";
    public static final String JOIN_GAME = "Присоединиться к игре";

    private final List<String> createOrJoinButtons;
    private final List<Game> games;
    private final Random rand;
    private final List<UserChat> userChats;

    public Bot() {
        super();
        userChats = new ArrayList<>();
        games = new ArrayList<>();
        rand = new Random();
        createOrJoinButtons = new ArrayList<>();
        createOrJoinButtons.add(CREATE_GAME);
        createOrJoinButtons.add(JOIN_GAME);
    }

    @Override
    public String getBotUsername() {
        return BotConfig.USERNAME;
    }

    @Override
    public String getBotToken() {
        return BotConfig.TOKEN;
    }

    public UserChat findUser(Long id) {
        for (UserChat user : userChats) {
            if (user.getId().equals(id))
                return user;
        }
        return null;
    }

    public UserChat findUser(String name) {
        for (UserChat user : userChats) {
            if (name != null && name.trim().equalsIgnoreCase(user.getName()))
                return user;
        }
        return null;
    }

    private Game findGame(int id) {
        for (Game game : games) {
            if (game.getId() == id)
                return game;
        }
        return null;
    }

    public int randIDForGame() {
        int id = rand.nextInt(10000);
        while (findGame(id) != null)
            id = rand.nextInt(10000);
        return id;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update == null || update.getMessage() == null)
                return;
            Message msg = update.getMessage();
            Long id = msg.getChatId();
            UserChat user = findUser(id);
            if (user != null) {
                if (user.getGame() == null) {
                    if (user.getStatus() == UserChat.JOIN_GAME) {
                        try {
                            int x = Integer.parseInt(msg.getText().trim());
                            Game game = findGame(x);
                            if (game == null || !game.addPlayer(user)) {
                                user.setStatus(UserChat.OK);
                                sendKeyBoard(id, "Выберите действие:", createOrJoinButtons);
                            }
                        } catch (Exception e) {
                            user.setStatus(UserChat.OK);
                            sendKeyBoard(user.getId(), "Неверный идентификатор игры. Попробуйте ещё раз.", createOrJoinButtons);
                        }
                    } else {
                        if (msg.hasText() && CREATE_GAME.equals(msg.getText())) {
                            Game game = new Game(this);
                            games.add(game);
                            user.setGame(game);
                            game.setId(randIDForGame());
                            sendText(id, "ID игры = " + game.getId());
                            if (!game.addPlayer(user))
                                sendKeyBoard(id, "Выберите действие:", createOrJoinButtons);
                        } else if (msg.hasText() && JOIN_GAME.equals(msg.getText())) {
                            user.setStatus(UserChat.JOIN_GAME);
                            sendText(id, "Введите ID игры");
                        } else {
                            sendKeyBoard(id, "Выберите действие:", createOrJoinButtons);
                        }
                    }
                } else {
                    user.getGame().readMsg(update);
                }
            } else {
                user = new UserChat(id, null);
                sendKeyBoard(id, "Добро пожаловать!", createOrJoinButtons);
                userChats.add(user);
                user.setStatus(UserChat.OK);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeGame(Game game) {
        games.remove(game);
    }

    public void forwardMessage(Long chatId, Message msg) {
        CopyMessage copyMessage = new CopyMessage();
        copyMessage.setMessageId(msg.getMessageId());
        copyMessage.setChatId(chatId.toString());
        copyMessage.setFromChatId(msg.getChatId().toString());
        try {
            execute(copyMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendText(Long chatId, String text) {
        SendMessage s = new SendMessage();
        s.setChatId(chatId.toString());
        s.setText(text);
        try {
            execute(s);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendKeyBoard(Long chatId, String text, List<String> buttons) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(false);
        List<KeyboardRow> keyboard = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 2) {
            KeyboardRow keyboardRow = new KeyboardRow();
            keyboardRow.add(buttons.get(i));
            if (i + 1 < buttons.size())
                keyboardRow.add(buttons.get(i + 1));
            keyboard.add(keyboardRow);
        }
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setKeyboard(keyboard);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}

