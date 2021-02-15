package org.mikhan808;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Bot extends TelegramLongPollingBot {

    public static final int COUNT_CARDS_ON_HANDS = 15;

    public static final int STANDARD_SCORING = 0;
    public static final int USER_SCORING = 1;

    public static final int FULL_ONLINE = 0;
    public static final int WITHOUT_CARDS = 1;

    private int countPlayers = 1000;
    private int countVotePlayers = 0;
    private int indexActivePlayer = 0;
    private int countRounds = 1;
    private final Random rand;
    private String associate;
    private int conspiratorIndex = 0;
    public static final int COUNT_CARD_ON_ROUND = 2;
    private static final String WORDS_FILE_NAME = "Words.csv";
    private static final String YES = "Да";
    private static final String NO = "Нет";
    private Stack<String> cards;
    private final List<String> yesno;
    private String card;

    public Bot() {
        super();
        userChats = new ArrayList<>();
        rand = new Random();
        cards = getCards();
        Collections.shuffle(cards, rand);
        yesno = new ArrayList<>();
        yesno.add(YES);
        yesno.add(NO);
    }

    List<UserChat> userChats;

    private static final String BEGIN_DISCUSSION = "Перейти к обсуждению";

    @Override
    public String getBotUsername() {
        return BotConfig.USERNAME;
    }

    @Override
    public String getBotToken() {
        return BotConfig.TOKEN;
    }

    private UserChat findUser(Long id)
    {
        for (UserChat user:userChats)
        {
            if(user.getId().equals(id))
                return user;
        }
        return null;
    }
    private UserChat findUser(String name)
    {

        for (UserChat user:userChats)
        {
            if(name.trim().equalsIgnoreCase(user.getName()))
                return user;
        }
        return null;
    }

    @Override
    public void onUpdateReceived(Update update) {

        Message msg = update.getMessage();
        Long id = msg.getChatId();
        UserChat user = findUser(id);
        if (user!=null) {
            if(user.getStatus()==UserChat.ENTER_NAME)
            {
                enterName(msg, user);
            } else if(user.getStatus()==UserChat.ENTER_COUNT_PLAYERS) {
                enterCountPlayers(msg, user);
            } else if (user.getStatus()==UserChat.OK)
            {
                sendText(id,"Ожидаем, потерпите)");
            }
            else if (user.getStatus()==UserChat.ACTIVE_PLAYER)
            {
                sendAssociate(msg, user);
            }else if (user.getStatus()==UserChat.ACTIVE_PLAYER_X) {
                if (BotConfig.TYPE_GAME == FULL_ONLINE)
                    checkCard(msg, user);
                else requestConfirmation(user, UserChat.ACTIVE_PLAYER_Z);
            } else if (user.getStatus() == UserChat.CONSPIRATOR) {
                sendText(id, "Ну зачем такие движения? не наводите на себя подозрения)");
            } else if (user.getStatus() == UserChat.VOTE) {
                checkVote(msg, user);
            } else if (user.getStatus() == UserChat.ACTIVE_PLAYER_Z) {
                if (msg.getText().equals(YES)) {
                    if (BotConfig.TYPE_GAME == FULL_ONLINE)
                        sendCard(user);
                    else runVote();
                } else {
                    if (BotConfig.TYPE_GAME == FULL_ONLINE) {
                        user.setStatus(UserChat.ACTIVE_PLAYER_X);
                        sendKeyBoard(user.getId(), "Выберите карточку", user.getCards());
                    } else {
                        sendButtonBeginDiscussion(user);
                    }
                }
            } else if (user.getStatus() == UserChat.VOTE_X) {
                if (msg.getText().equals(YES)) {
                    if (userChats.indexOf(user) != conspiratorIndex)
                        user.getVoteUser().incVotes();
                    user.setGuessed(userChats.indexOf(user.getVoteUser()) == conspiratorIndex);
                    countVotePlayers++;
                    if (countVotePlayers < userChats.size() - 1) {
                        user.setStatus(UserChat.OK);
                        sendText(id, "Ожидайте других игроков");
                    } else {
                        user.setStatus(UserChat.OK);
                        sendText(id, "Ожидайте других игроков");
                        calculateScores();
                        String usersScore = buildListUsersScore();
                        sendTextToAll(usersScore);
                        boolean end = false;
                        if (indexActivePlayer < userChats.size() - 1)
                            indexActivePlayer++;
                        else if (countRounds == 1) {
                            sendTextToAll("Конец игры");
                            end = true;
                        } else {
                            countRounds--;
                            indexActivePlayer = 0;
                        }
                        if (!end) {
                            nextRound();
                        }
                    }
                } else {
                    user.setStatus(UserChat.VOTE);
                    sendKeyBoard(id, "Проголосуйте еще раз", calculateButtonsForVote(user));
                }

            }

        } else {
            if (msg.getText().contentEquals(BotConfig.PASSWORD) && userChats.size() < countPlayers) {
                user = new UserChat(id, null);
                user.setStatus(UserChat.ENTER_NAME);
                sendText(id, "Добро пожаловать");
                sendText(id, "Пожалуйста введите свое имя");
                userChats.add(user);
            } else sendText(id, "Введите пароль");
        }
    }

    private void calculateScores() {
        if (getConspirator().getVotes() > 1) {
            getConspirator().setCurrentRoundScore(0);
            getActivePlayer().setCurrentRoundScore(0);
        } else {
            getConspirator().setCurrentRoundScore(5);
            getActivePlayer().setCurrentRoundScore(4);
        }
        for (int i = 0; i < userChats.size(); i++) {
            if (i != conspiratorIndex && i != indexActivePlayer) {
                if (userChats.get(i).isGuessed())
                    userChats.get(i).setCurrentRoundScore(3);
                else
                    userChats.get(i).setCurrentRoundScore(0);
                if (BotConfig.TYPE_SCORING == USER_SCORING)
                    userChats.get(i).setDeductedPoints(userChats.get(i).getVotes());
            } else {
                userChats.get(i).setDeductedPoints(0);
            }
        }
    }

    private String buildListUsersScore() {
        StringBuilder sb = new StringBuilder();
        sb.append("Конспиратор - ").append(getConspirator().getName()).append("\n");
        UserChat[] usersScore = sortedUsers();
        for (int i = 0; i < usersScore.length; i++) {
            sb.append(i + 1).append(". ").append(usersScore[i].getName()).append(" = ").append(usersScore[i].getScore());
            sb.append(" (").append(usersScore[i].getCurrentRoundScore()).append(" -").append(usersScore[i].getDeductedPoints()).append(")\n");
        }
        return sb.toString();
    }

    private UserChat[] sortedUsers() {
        UserChat[] usersScore = new UserChat[userChats.size()];
        usersScore = userChats.toArray(usersScore);
        for (int i = 0; i < usersScore.length; i++)
            for (int g = 0; g < usersScore.length - 1; g++) {
                if (usersScore[g + 1].getScore() > usersScore[g].getScore()) {
                    UserChat temp = usersScore[g];
                    usersScore[g] = usersScore[g + 1];
                    usersScore[g + 1] = temp;
                }
            }
        return usersScore;
    }

    private void nextRound() {
        UserChat activePlayer = getActivePlayer();
        activePlayer.setStatus(UserChat.ACTIVE_PLAYER);
        for (UserChat userChat : userChats) {
            userChat.resetTable();
            for (int i = 0; i < COUNT_CARD_ON_ROUND; i++)
                userChat.addCard(getNextCard());
            sendText(userChat.getId(), "Следующий раунд");
            sendText(userChat.getId(), activePlayer.getName() + " - активный игрок");
            sendKeyBoard(userChat.getId(), "Ожидаем ассоциацию", userChat.getCards());
        }
    }

    private void requestConfirmation(UserChat user, int status) {
        user.setStatus(status);
        sendKeyBoard(user.getId(), "Вы уверены?", yesno);
    }

    private String getNextCard() {
        if (cards.empty()) {
            cards = getCards();
            Collections.shuffle(cards, rand);
        }
        return cards.pop();
    }

    private void sendCard(UserChat user) {
        StringBuilder sb = new StringBuilder();
        user.moveCardToTable(card);
        for (int i = indexActivePlayer, g = 0; g < userChats.size(); g++) {
            if (userChats.get(i).getTable().size() > 0) {
                sb.append(userChats.get(i).getName());
                sb.append(" (");
                if (userChats.get(i).getTable().size() > 1) {
                    sb.append(userChats.get(i).getTable().get(0)).append(", ").append(userChats.get(i).getTable().get(1));
                } else {
                    sb.append(userChats.get(i).getTable().get(0));
                }
                sb.append(")\n");
            }
            i++;
            if (i >= userChats.size())
                i = 0;
        }
        sendKeyBoard(user.getId(), "Ок", user.getCards());
        sendTextToAll(sb.toString());
        int ind = userChats.indexOf(user);
        ind++;
        if (ind >= userChats.size())
            ind = 0;
        user.setStatus(UserChat.OK);
        userChats.get(ind).setStatus(UserChat.ACTIVE_PLAYER_X);
        if (userChats.get(ind).getTable().size() >= COUNT_CARD_ON_ROUND) {
            runVote();
        } else {
            sendTextToAll(userChats.get(ind).getName() + " кладет карточку");
            sendKeyBoard(userChats.get(ind).getId(), "Выберите слово", userChats.get(ind).getCards());
        }
    }

    private void checkVote(Message msg, UserChat user) {
        UserChat voteUser = findUser(msg.getText());
        if (voteUser == null)
            sendText(user.getId(), "Пожалуйста выберите игрока с помощью кнопок");
        else {
            user.setVoteUser(voteUser);
            requestConfirmation(user, UserChat.VOTE_X);
        }
    }

    private void checkCard(Message msg, UserChat user) {
        card = "";
        for (String c : user.getCards()) {
            if (c.equals(msg.getText()))
                card = c;
        }
        if (!card.isEmpty()) {
            requestConfirmation(user, UserChat.ACTIVE_PLAYER_Z);
        } else {
            sendText(user.getId(), "Воспользуйтесь кнопками бота для отправки слова");
        }
    }

    private void sendAssociate(Message msg, UserChat user) {
        associate = msg.getText();
        conspiratorIndex = rand.nextInt(userChats.size());
        while (conspiratorIndex == indexActivePlayer)
            conspiratorIndex = rand.nextInt(userChats.size());
        getConspirator().setStatus(UserChat.CONSPIRATOR);
        for (UserChat player : userChats) {
            if (player.getStatus() == UserChat.CONSPIRATOR)
                sendText(player.getId(), "Вы конспиратор");
            else sendText(player.getId(), associate);
        }
        if (BotConfig.TYPE_GAME == FULL_ONLINE) {
            user.setStatus(UserChat.ACTIVE_PLAYER_X);
            sendKeyBoard(user.getId(), "Отправьте первое слово", user.getCards());
        } else {
            sendButtonBeginDiscussion(user);
        }
    }

    private void sendButtonBeginDiscussion(UserChat user) {
        user.setStatus(UserChat.ACTIVE_PLAYER_X);
        List<String> buttons = new ArrayList<>();
        buttons.add(BEGIN_DISCUSSION);
        sendKeyBoard(user.getId(), "Нажмите на кнопку, когда все положат по 2 карточки", buttons);
    }

    private void enterCountPlayers(Message msg, UserChat user) {

        if (msg.getText() != null)
            try {
                countPlayers = Integer.parseInt(msg.getText().trim());
                user.setStatus(UserChat.OK);
                sendText(user.getId(), "Ожидаем подключения всех игроков");
            } catch (Exception e) {
                e.printStackTrace();
                sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterName(Message msg, UserChat user) {
        Long id = user.getId();
        String name = msg.getText();
        if (findUser(name) == null) {
            user.setName(name);
            if (userChats.indexOf(user) == 0) {
                user.setStatus(UserChat.ENTER_COUNT_PLAYERS);
                sendText(id, "Введите количество игроков");
            } else {
                user.setStatus(UserChat.OK);
                if (userChats.size() < countPlayers || userChats.indexOf(user) != userChats.size() - 1)
                    sendText(id, "Ожидаем подключения всех игроков");
                else {
                    beginGame();
                }
            }
        } else sendText(id, "Пожалуйста введите другое имя, данное имя уже занято");
    }

    private void beginGame() {
        sendTextToAll("Начинаем!");
        for (UserChat userChat : userChats) {
            for (int i = 0; i < COUNT_CARDS_ON_HANDS - COUNT_CARD_ON_ROUND; i++) {
                userChat.addCard(getNextCard());
            }
        }
        nextRound();
    }

    private UserChat getConspirator() {
        return userChats.get(conspiratorIndex);
    }

    private UserChat getActivePlayer() {
        return userChats.get(indexActivePlayer);
    }

    private List<String> calculateButtonsForVote(UserChat user) {
        List<String> buttons = new ArrayList<>();
        for (int i = 0; i < userChats.size(); i++) {
            if (i != indexActivePlayer && !userChats.get(i).getId().equals(user.getId())) {
                buttons.add(userChats.get(i).getName());
            }

        }
        return buttons;
    }

    private void runVote() {
        countVotePlayers = 0;
        sendTextToAll("Ассоциация:");
        sendTextToAll(associate);
        for (UserChat userChat : userChats) {
            userChat.resetVotes();
            userChat.setGuessed(false);
            if (userChat.getStatus() == UserChat.ACTIVE_PLAYER_X) {
                userChat.setStatus(UserChat.OK);
                sendText(userChat.getId(), "Объясните почему Вы положили именно эти карточки и ждите результата голосования");
            } else {
                userChat.setStatus(UserChat.VOTE);
                sendKeyBoard(userChat.getId(), "Расскажите почему Вы положили именно эти карточки," +
                        "выслушайте других участников и проголосуйте за того,"
                        + " кто по вашему мнению является конспиратором", calculateButtonsForVote(userChat));
            }
        }
    }

    private void sendTextToAll(String text) {
        for (UserChat user : userChats) {
            sendText(user.getId(), text);
        }
    }

    private void sendText(Long chatId, String text) {
        SendMessage s = new SendMessage();
        s.setChatId(chatId.toString()); // Боту может писать не один человек, и поэтому чтобы отправить сообщение, грубо говоря нужно узнать куда его отправлять
        s.setText(text);
        try { //Чтобы не крашнулась программа при вылете Exception
            execute(s);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendKeyBoard(Long chatId,String text,List<String> buttons)
    {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        for(int i=0;i<buttons.size();i+=2) {
            KeyboardRow keyboardRow = new KeyboardRow();
            keyboardRow.add(buttons.get(i));
            if(i+1<buttons.size())
            keyboardRow.add(buttons.get(i+1));
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


    Stack<String> getCards() {
        Stack<String> result = new Stack<>();
        try {
            File file = new File(WORDS_FILE_NAME);
            //создаем объект FileReader для объекта File
            FileReader fr = new FileReader(file);
            //создаем BufferedReader с существующего FileReader для построчного считывания
            BufferedReader reader = new BufferedReader(fr);
            // считаем сначала первую строку
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    result.push(line);
                }
                // считываем остальные строки в цикле
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

}
