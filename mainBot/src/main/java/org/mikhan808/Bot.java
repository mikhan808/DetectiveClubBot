package org.mikhan808;

import org.mikhan808.core.Game;
import org.mikhan808.core.LobbyUserChat;
import org.mikhan808.core.UserChat;
import org.mikhan808.games.cardgames.decoder.DecoderGame;
import org.mikhan808.games.cardgames.detectiveclub.DetectiveClubGame;
import org.mikhan808.games.cardgames.dixit.DixitGame;
import org.mikhan808.games.resistance.ResistanceGame;
import org.mikhan808.games.quiz.MultiplicationGame;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Bot extends TelegramLongPollingBot {

    public static final String YES = "Да";
    public static final String NO = "Нет";
    public static final String CREATE_GAME = "Создать игру";
    public static final String JOIN_GAME = "Присоединиться";

    private final List<String> createOrJoinButtons;
    private final List<String> gameTypeButtons;
    private static final String CHAT_LOG_FILE = "chats.csv";
    private static final String CHAT_LOG_HEADER = "chat_id,chat_type,chat_title,chat_username,chat_first_name,chat_last_name,message_date\n";
    private final Set<Long> loggedChats;
    List<Game> games;
    Random rand;

    List<UserChat> userChats;

    public Bot() {
        super();
        userChats = new ArrayList<>();
        games = new ArrayList<>();
        rand = new Random();
        loggedChats = new HashSet<>();
        loadLoggedChatsFromFile();
        createOrJoinButtons = new ArrayList<>();
        createOrJoinButtons.add(CREATE_GAME);
        createOrJoinButtons.add(JOIN_GAME);
        gameTypeButtons = new ArrayList<>();
        gameTypeButtons.add(DetectiveClubGame.NAME);
        gameTypeButtons.add(DecoderGame.NAME);
        gameTypeButtons.add(ResistanceGame.NAME);
        gameTypeButtons.add(DixitGame.NAME);
        gameTypeButtons.add(MultiplicationGame.NAME);
        gameTypeButtons.add(JOIN_GAME);
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
            Message msg = update.getMessage();
            if (msg == null) return;
            logChatInfo(msg);
            Long id = msg.getChatId();
            UserChat user = findUser(id);
            if (user != null) {
                if (user.getGame() == null) {
                    if (user.getStatus() == UserChat.JOIN_GAME) {
                        if (msg.getText().equals(CREATE_GAME)) {
                            user.setStatus(UserChat.SELECT_GAME_TYPE);
                            sendKeyBoard(id, "Выберите игру", gameTypeButtons);
                            return;
                        }
                        try {
                            int x = Integer.parseInt(msg.getText().trim());
                            Game game = findGame(x);
                            if (game == null || !game.addPlayer(user)) {
                                user.setStatus(UserChat.OK);
                                sendKeyBoard(id, "Главное меню", createOrJoinButtons);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            user.setStatus(UserChat.OK);
                            sendKeyBoard(user.getId(), "Введите корректный ID. Возврат в меню.", createOrJoinButtons);
                        }
                    } else {
                        if (msg.getText().equals(CREATE_GAME)) {
                            user.setStatus(UserChat.SELECT_GAME_TYPE);
                            sendKeyBoard(id, "Выберите игру", gameTypeButtons);
                        } else if (user.getStatus() == UserChat.SELECT_GAME_TYPE) {
                            if (msg.getText().equals(JOIN_GAME)) {
                                user.setStatus(UserChat.JOIN_GAME);
                                sendText(id, "Введите ID игры");
                                return;
                            }
                            String choice = msg.getText();
                            Game game = null;
                            if (DetectiveClubGame.NAME.equals(choice)) {
                                game = new DetectiveClubGame(this);
                            } else if (DecoderGame.NAME.equals(choice)) {
                                game = new DecoderGame(this);
                            } else if (ResistanceGame.NAME.equals(choice)) {
                                game = new ResistanceGame(this);
                            } else if (DixitGame.NAME.equals(choice)) {
                                game = new DixitGame(this);
                            } else if (MultiplicationGame.NAME.equals(choice)) {
                                game = new MultiplicationGame(this);
                            }
                            if (game != null) {
                                games.add(game);
                                user.setGame(game);
                                game.setId(randIDForGame());
                                sendText(id, "ID игры = " + game.getId());
                                if (!game.addPlayer(user))
                                    sendKeyBoard(id, "Главное меню", createOrJoinButtons);
                            } else {
                                sendKeyBoard(id, "Выберите игру", gameTypeButtons);
                            }
                        } else if (msg.getText().equals(JOIN_GAME)) {
                            user.setStatus(UserChat.JOIN_GAME);
                            sendText(id, "Введите ID игры");
                        } else {
                            sendKeyBoard(id, "Главное меню", createOrJoinButtons);
                        }

                    }
                } else user.getGame().readMsg(update);
            } else {
                user = new LobbyUserChat(id, null);
                sendKeyBoard(id, "Привет! Выберите действие", createOrJoinButtons);
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

    private void logChatInfo(Message msg) {
        Chat chat = msg.getChat();
        if (chat == null) return;
        Long chatId = chat.getId();
        boolean alreadyLogged;
        synchronized (loggedChats) {
            alreadyLogged = !loggedChats.add(chatId);
        }
        Path path = Paths.get(CHAT_LOG_FILE);
        boolean fileExists = Files.exists(path);
        try {
            if (!fileExists) {
                Files.createFile(path);
            }
        } catch (IOException ignored) {
            return;
        }
        if (alreadyLogged) return;
        try {
            if (!fileExists) {
                Files.write(path,
                        CHAT_LOG_HEADER.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.APPEND);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(chatId).append(',');
            sb.append(toCsv(chat.getType())).append(',');
            sb.append(toCsv(chat.getTitle())).append(',');
            sb.append(toCsv(chat.getUserName())).append(',');
            sb.append(toCsv(chat.getFirstName())).append(',');
            sb.append(toCsv(chat.getLastName())).append(',');
            Integer date = msg.getDate();
            sb.append(toCsv(date != null ? String.valueOf(date) : null)).append('\n');
            Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String toCsv(String value) {
        if (value == null) return "\"\"";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private void loadLoggedChatsFromFile() {
        Path path = Paths.get(CHAT_LOG_FILE);
        if (!Files.exists(path)) return;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.isEmpty()) continue;
                int commaIndex = line.indexOf(',');
                String idPart = commaIndex >= 0 ? line.substring(0, commaIndex) : line;
                try {
                    long id = Long.parseLong(idPart.trim());
                    loggedChats.add(id);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    public void forwardMessage(Long chatId, Message msg) {
        ForwardMessage copyMessage = new ForwardMessage();
        copyMessage.setMessageId(msg.getMessageId());
        copyMessage.setChatId(chatId.toString());
        copyMessage.setFromChatId(msg.getChatId().toString());
        try {
            execute(copyMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void copyMessage(Long chatId, Message msg) {
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
        sendMessage.enableMarkdown(true);
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
