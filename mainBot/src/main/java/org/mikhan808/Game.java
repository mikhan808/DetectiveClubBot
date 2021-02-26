package org.mikhan808;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static org.mikhan808.Bot.NO;
import static org.mikhan808.Bot.YES;

public class Game {
    public static final int STANDARD_SCORING = 0;
    public static final int USER_SCORING = 1;

    public static final int MIN_COUNT_PLAYERS = 4;
    public static final int FULL_ONLINE = 0;
    public static final int WITHOUT_CARDS = 1;
    private static final String BEGIN_DISCUSSION = "Перейти к обсуждению";
    private static final String WORDS_FILE_NAME = "Words.csv";
    private final int count_card_on_round = 2;
    private final int conspirator_score = 3;
    private final int active_player_score = 3;
    private final int guessed_score = 3;
    private final int deducted_score = 1;
    private final Random rand;
    private final List<UserChat> userChats;
    private final int type_scoring = USER_SCORING;
    private final Bot bot;
    private final List<String> yesno;
    int enterNames = 0;
    boolean finishSetting = false;
    private int id;
    private Stack<String> cards;
    private int countPlayers = 1000;
    private int count_cards_on_hands = 12;
    private int countVotePlayers = 0;
    private int indexActivePlayer = 0;
    private int countRounds = 2;
    private Message associate;
    private int conspiratorIndex = 0;
    private String card;
    private int type_game = FULL_ONLINE;

    public Game(Bot bot) {
        this.bot = bot;
        userChats = new ArrayList<>();
        rand = new Random();
        cards = getCards();
        Collections.shuffle(cards, rand);
        yesno = new ArrayList<>();
        yesno.add(YES);
        yesno.add(NO);
    }

    private UserChat findUser(Long id) {
        for (UserChat user : userChats) {
            if (user.getId().equals(id))
                return user;
        }
        return null;
    }

    private UserChat findUser(String name) {

        for (UserChat user : userChats) {
            if (name.trim().equalsIgnoreCase(user.getName()))
                return user;
        }
        return null;
    }

    public void readMsg(Update update) {
        Message msg = update.getMessage();
        Long id = msg.getChatId();
        UserChat user = findUser(id);
        if (user != null) {
            if (msg.hasText() && msg.getText().equals("/exit")) {
                sendTextToAll(user.getName() + " завершил игру");
                finishGame();
            }
            if (user.getStatus() == UserChat.ENTER_NAME) {
                enterName(msg, user);
            } else if (user.getStatus() == UserChat.ENTER_COUNT_PLAYERS) {
                enterCountPlayers(msg, user);
            } else if (user.getStatus() == UserChat.ENTER_TYPE_GAME) {
                enterTypeGame(msg, user);
            } else if (user.getStatus() == UserChat.ENTER_COUNT_ROUNDS) {
                enterCountRounds(msg, user);
            } else if (user.getStatus() == UserChat.ENTER_COUNT_CARDS) {
                enterCountCards(msg, user);
            } else if (user.getStatus() == UserChat.OK) {
                bot.sendText(id, "Ожидаем, потерпите)");
            } else if (user.getStatus() == UserChat.ACTIVE_PLAYER) {
                boolean error = false;
                if (msg.hasText()) {
                    if (msg.getText().toLowerCase().contains("вы конспиратор")) {
                        bot.sendText(id, "ассоциация не должна содержать слово 'конспиратор'");
                        error = true;
                    }
                    for (String c : user.getCards())
                        if (c.equalsIgnoreCase(msg.getText())) {
                            bot.sendText(id, "ассоциация не должна быть из ваших карточек");
                            error = true;
                        }

                }
                if (!error)
                    sendAssociate(msg, user);
            } else if (user.getStatus() == UserChat.ACTIVE_PLAYER_X) {
                if (type_game == FULL_ONLINE)
                    checkCard(msg, user);
                else requestConfirmation(user, UserChat.ACTIVE_PLAYER_Z);
            } else if (user.getStatus() == UserChat.CONSPIRATOR) {
                bot.sendText(id, "Ну зачем такие движения? не наводите на себя подозрения)");
            } else if (user.getStatus() == UserChat.VOTE) {
                checkVote(msg, user);
            } else if (user.getStatus() == UserChat.ACTIVE_PLAYER_Z) {
                if (msg.getText().equals(YES)) {
                    if (type_game == FULL_ONLINE)
                        sendCard(user);
                    else runVote();
                } else {
                    if (type_game == FULL_ONLINE) {
                        user.setStatus(UserChat.ACTIVE_PLAYER_X);
                        bot.sendKeyBoard(user.getId(), "Выберите карточку", user.getCards());
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
                        bot.sendText(id, "Ожидайте других игроков");
                    } else {
                        user.setStatus(UserChat.OK);
                        bot.sendText(id, "Ожидайте других игроков");
                        calculateScores();
                        String usersScore = buildListUsersScore();
                        sendTextToAll(usersScore);
                        boolean end = false;
                        if (indexActivePlayer < userChats.size() - 1)
                            indexActivePlayer++;
                        else if (countRounds == 1) {
                            finishGame();
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
                    bot.sendKeyBoard(id, "Проголосуйте еще раз", calculateButtonsForVote(user));
                }

            }

        } else {
            bot.sendText(id, "Почему то вас нет в списках попробуйте присоединиться к другой игре");
        }
    }

    private void finishGame() {
        sendTextToAll("Конец игры");
        for (UserChat user : userChats) {
            user.setGame(null);
        }
        bot.removeGame(this);
    }

    public boolean addPlayer(UserChat user) {
        if (userChats.size() < countPlayers) {
            user.setStatus(UserChat.ENTER_NAME);
            user.setGame(this);
            bot.sendText(user.getId(), "Добро пожаловать");
            bot.sendText(user.getId(), "Пожалуйста введите свое имя");
            userChats.add(user);
            return true;
        } else bot.sendText(user.getId(), "Количество игроков уже собрано попробуйте присоединиться к другой игре");
        return false;
    }

    private void sendTextToAll(String text) {
        for (UserChat user : userChats) {
            bot.sendText(user.getId(), text);
        }
    }

    private void forwardMsgToAll(Message message) {
        for (UserChat user : userChats) {
            bot.forwardMessage(user.getId(), message);
        }
    }

    private void calculateScores() {
        if (getConspirator().getVotes() > 1) {
            getConspirator().setCurrentRoundScore(0);
            getActivePlayer().setCurrentRoundScore(0);
        } else if (getConspirator().getVotes() == 1) {
            getConspirator().setCurrentRoundScore(conspirator_score - 1);
            getActivePlayer().setCurrentRoundScore(active_player_score - 1);
        } else {
            getConspirator().setCurrentRoundScore(conspirator_score);
            getActivePlayer().setCurrentRoundScore(active_player_score);
        }
        for (int i = 0; i < userChats.size(); i++) {
            if (i != conspiratorIndex && i != indexActivePlayer) {
                if (userChats.get(i).isGuessed())
                    userChats.get(i).setCurrentRoundScore(guessed_score);
                else
                    userChats.get(i).setCurrentRoundScore(0);
                userChats.get(i).setDeductedPoints(userChats.get(i).getVotes() * deducted_score);
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
            for (int i = 0; i < count_card_on_round; i++)
                userChat.addCard(getNextCard());
            bot.sendText(userChat.getId(), "Следующий раунд");
            bot.sendText(userChat.getId(), activePlayer.getName() + " - активный игрок");
            bot.sendKeyBoard(userChat.getId(), "Ожидаем ассоциацию", userChat.getCards());
        }
    }

    private void requestConfirmation(UserChat user, int status) {
        user.setStatus(status);
        bot.sendKeyBoard(user.getId(), "Вы уверены?", yesno);
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
                for (int x = 0; x < userChats.get(i).getTable().size(); x++) {
                    if (x != 0)
                        sb.append(", ");
                    sb.append(userChats.get(i).getTable().get(x));
                }
                sb.append(")\n");
            }
            i++;
            if (i >= userChats.size())
                i = 0;
        }
        bot.sendKeyBoard(user.getId(), "Ок", user.getCards());
        sendTextToAll(sb.toString());
        int ind = userChats.indexOf(user);
        ind++;
        if (ind >= userChats.size())
            ind = 0;
        user.setStatus(UserChat.OK);
        userChats.get(ind).setStatus(UserChat.ACTIVE_PLAYER_X);
        if (userChats.get(ind).getTable().size() >= count_card_on_round) {
            runVote();
        } else {
            sendTextToAll(userChats.get(ind).getName() + " кладет карточку");
            bot.sendKeyBoard(userChats.get(ind).getId(), "Выберите слово", userChats.get(ind).getCards());
        }
    }

    private void checkVote(Message msg, UserChat user) {
        UserChat voteUser = findUser(msg.getText());
        if (voteUser == null)
            bot.sendText(user.getId(), "Пожалуйста выберите игрока с помощью кнопок");
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
            bot.sendText(user.getId(), "Воспользуйтесь кнопками бота для отправки слова");
        }
    }

    private void sendAssociate(Message msg, UserChat user) {
        associate = msg;
        conspiratorIndex = rand.nextInt(userChats.size());
        while (conspiratorIndex == indexActivePlayer)
            conspiratorIndex = rand.nextInt(userChats.size());
        getConspirator().setStatus(UserChat.CONSPIRATOR);
        for (UserChat player : userChats) {
            if (player.getStatus() == UserChat.CONSPIRATOR)
                bot.sendText(player.getId(), "Вы конспиратор");
            else bot.forwardMessage(player.getId(), associate);
        }
        if (type_game == FULL_ONLINE) {
            user.setStatus(UserChat.ACTIVE_PLAYER_X);
            bot.sendKeyBoard(user.getId(), "Отправьте первое слово", user.getCards());
        } else {
            sendButtonBeginDiscussion(user);
        }
    }

    private void sendButtonBeginDiscussion(UserChat user) {
        user.setStatus(UserChat.ACTIVE_PLAYER_X);
        List<String> buttons = new ArrayList<>();
        buttons.add(BEGIN_DISCUSSION);
        bot.sendKeyBoard(user.getId(), "Нажмите на кнопку, когда все положат по 2 карточки", buttons);
    }

    private void enterCountPlayers(Message msg, UserChat user) {

        if (msg.getText() != null)
            try {
                int x = Integer.parseInt(msg.getText().trim());
                if (x >= MIN_COUNT_PLAYERS) {
                    countPlayers = x;
                    user.setStatus(UserChat.ENTER_TYPE_GAME);
                    bot.sendKeyBoard(user.getId(), "Использовать онлайн карточки?", yesno);
                } else {
                    bot.sendText(user.getId(), "Количество игроков не может быть меньше " + MIN_COUNT_PLAYERS);
                }
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterTypeGame(Message msg, UserChat user) {

        if (msg.getText() != null)
            try {
                if (msg.getText().equals(YES))
                    type_game = FULL_ONLINE;
                else type_game = WITHOUT_CARDS;
                user.setStatus(UserChat.ENTER_COUNT_ROUNDS);
                bot.sendText(user.getId(), "Введите количество раундов");
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterCountRounds(Message msg, UserChat user) {

        if (msg.getText() != null)
            try {
                countRounds = Integer.parseInt(msg.getText());
                user.setStatus(UserChat.ENTER_COUNT_CARDS);
                bot.sendText(user.getId(), "Введите количество карточек на руках");
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterCountCards(Message msg, UserChat user) {

        if (msg.getText() != null)
            try {
                count_cards_on_hands = Integer.parseInt(msg.getText());
                user.setStatus(UserChat.OK);
                bot.sendText(user.getId(), "Ожидаем подключения всех игроков");
                finishSetting = true;
                if (enterNames == countPlayers)
                    beginGame();
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterName(Message msg, UserChat user) {
        Long id = user.getId();
        String name = msg.getText();
        UserChat userForName = findUser(name);
        if (userForName == null || userForName == user) {
            user.setName(name);
            enterNames++;
            if (userChats.indexOf(user) == 0) {
                user.setStatus(UserChat.ENTER_COUNT_PLAYERS);
                bot.sendText(id, "Введите количество игроков");
            } else {
                user.setStatus(UserChat.OK);
                if (enterNames < countPlayers || !finishSetting)
                    bot.sendText(id, "Ожидаем подключения всех игроков");
                else {
                    beginGame();
                }
            }
        } else bot.sendText(id, "Пожалуйста введите другое имя, данное имя уже занято");
    }

    private void beginGame() {
        sendTextToAll("Начинаем!");
        for (UserChat userChat : userChats) {
            userChat.getCards().clear();
            userChat.setScore(0);
            for (int i = 0; i < count_cards_on_hands - count_card_on_round; i++) {
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
        forwardMsgToAll(associate);
        for (UserChat userChat : userChats) {
            userChat.resetVotes();
            userChat.setGuessed(false);
            if (userChat.getStatus() == UserChat.ACTIVE_PLAYER_X) {
                userChat.setStatus(UserChat.OK);
                bot.sendText(userChat.getId(), "Объясните почему Вы положили именно эти карточки и ждите результата голосования");
            } else {
                userChat.setStatus(UserChat.VOTE);
                bot.sendKeyBoard(userChat.getId(), "Расскажите почему Вы положили именно эти карточки," +
                        "выслушайте других участников и проголосуйте за того,"
                        + " кто по вашему мнению является конспиратором", calculateButtonsForVote(userChat));
            }
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
