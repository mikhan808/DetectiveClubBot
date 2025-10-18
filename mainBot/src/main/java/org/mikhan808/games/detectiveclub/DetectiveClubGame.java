package org.mikhan808.games.detectiveclub;

import org.mikhan808.Bot;
import org.mikhan808.core.Game;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.mikhan808.Bot.NO;
import static org.mikhan808.Bot.YES;

public class DetectiveClubGame extends Game {
    public static final int USER_SCORING = 1;

    public static final int MIN_COUNT_PLAYERS = 4;
    public static final int FULL_ONLINE = 0;
    public static final int WITHOUT_CARDS = 1;
    private static final String BEGIN_DISCUSSION = "Перейти к обсуждению";
    private static final String WORDS_RESOURCE_NAME = "Words.csv";
    private final int count_card_on_round = 2;
    private final int conspirator_score = 3;
    private final int active_player_score = 3;
    private final int guessed_score = 3;
    private final int deducted_score = 1;
    private final Random rand;
    private final List<DetectiveUserChat> userChats;
    private final int type_scoring = USER_SCORING;
    private final List<String> yesno;
    int enterNames = 0;
    boolean finishSetting = false;

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

    public DetectiveClubGame(Bot bot) {
        super(bot);
        userChats = new ArrayList<>();
        rand = new Random();
        cards = getCards();
        Collections.shuffle(cards, rand);
        yesno = new ArrayList<>();
        yesno.add(YES);
        yesno.add(NO);
    }

    private DetectiveUserChat findUser(Long id) {
        for (DetectiveUserChat user : userChats) {
            if (user.getId().equals(id))
                return user;
        }
        return null;
    }

    private DetectiveUserChat findUser(String name) {
        for (DetectiveUserChat user : userChats) {
            if (name.trim().equalsIgnoreCase(user.getName()))
                return user;
        }
        return null;
    }

    public void readMsg(Update update) {
        Message msg = update.getMessage();
        Long id = msg.getChatId();
        DetectiveUserChat user = findUser(id);
        if (user != null) {
            if (msg.hasText() && msg.getText().equals("/exit")) {
                userChats.remove(user);
                user.setGame(null);
                if (userChats.size() >= MIN_COUNT_PLAYERS) {
                    sendTextToAll(user.getName() + " покинул игру");
                    countPlayers--;
                } else {
                    sendTextToAll(user.getName() + " завершил игру");
                    finishGame();
                }
            }
            if (user.getStatus() == DetectiveUserChat.ENTER_NAME) {
                enterName(msg, user);
                tryAutoStart();
            } else if (user.getStatus() == DetectiveUserChat.ENTER_COUNT_PLAYERS) {
                enterCountPlayers(msg, user);
                tryAutoStart();
            } else if (user.getStatus() == DetectiveUserChat.ENTER_TYPE_GAME) {
                enterTypeGame(msg, user);
                tryAutoStart();
            } else if (user.getStatus() == DetectiveUserChat.ENTER_COUNT_ROUNDS) {
                enterCountRounds(msg, user);
                tryAutoStart();
            } else if (user.getStatus() == DetectiveUserChat.ENTER_COUNT_CARDS) {
                enterCountCards(msg, user);
                tryAutoStart();
            } else if (user.getStatus() == DetectiveUserChat.OK) {
                bot.sendText(id, "Ожидаем, потерпите)");
            } else if (user.getStatus() == DetectiveUserChat.ACTIVE_PLAYER) {
                boolean error = false;
                if (msg.hasText()) {
                    if (msg.getText().toLowerCase().contains("конспиратор")) {
                        bot.sendText(id, "Ассоциация не должна содержать слово 'конспиратор'");
                        error = true;
                    }
                    for (String c : user.getCards()) {
                        if (c.equalsIgnoreCase(msg.getText())) {
                            bot.sendText(id, "Ассоциация не должна совпадать с текстом вашей карты");
                            error = true;
                        }
                    }
                }
                if (!error)
                    sendAssociate(msg, user);
            } else if (user.getStatus() == DetectiveUserChat.ACTIVE_PLAYER_X) {
                if (type_game == FULL_ONLINE)
                    checkCard(msg, user);
                else requestConfirmation(user, DetectiveUserChat.ACTIVE_PLAYER_Z);
            } else if (user.getStatus() == DetectiveUserChat.CONSPIRATOR) {
                bot.sendText(id, "Вы — конспиратор. Постарайтесь не выдать себя");
            } else if (user.getStatus() == DetectiveUserChat.VOTE) {
                checkVote(msg, user);
            } else if (user.getStatus() == DetectiveUserChat.ACTIVE_PLAYER_Z) {
                if (msg.getText().equals(YES)) {
                    if (type_game == FULL_ONLINE)
                        sendCard(user);
                    else runVote();
                } else {
                    if (type_game == FULL_ONLINE) {
                        user.setStatus(DetectiveUserChat.ACTIVE_PLAYER_X);
                        bot.sendKeyBoard(user.getId(), "Выберите карточку", user.getCards());
                    } else {
                        sendButtonBeginDiscussion(user);
                    }
                }
            } else if (user.getStatus() == DetectiveUserChat.VOTE_X) {
                if (msg.getText().equals(YES)) {
                    if (userChats.indexOf(user) != conspiratorIndex)
                        user.getVoteUser().incVotes();
                    user.setGuessed(userChats.indexOf(user.getVoteUser()) == conspiratorIndex);
                    countVotePlayers++;
                    if (countVotePlayers < userChats.size() - 1) {
                        user.setStatus(DetectiveUserChat.OK);
                        bot.sendText(id, "Ожидайте других игроков");
                    } else {
                        user.setStatus(DetectiveUserChat.OK);
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
                    user.setStatus(DetectiveUserChat.VOTE);
                    bot.sendKeyBoard(id, "Проголосуйте ещё раз", calculateButtonsForVote(user));
                }
            }
        } else {
            bot.sendText(id, "Почему-то вас нет в списках — попробуйте присоединиться к другой игре");
            if (bot.findUser(id) != null) bot.findUser(id).setGame(null);
        }
    }

    // Автостарт игры с настройками по умолчанию, если ведущий не задал параметры
    private void tryAutoStart() {
        if (finishSetting) return;
        // если параметры не заданы (значение по умолчанию) и собралось >= MIN_COUNT_PLAYERS
        if (countPlayers == 1000) {
            int named = 0;
            for (DetectiveUserChat u : userChats) if (u.getName() != null && !u.getName().trim().isEmpty()) named++;
            if (userChats.size() >= MIN_COUNT_PLAYERS && named == userChats.size()) {
                countPlayers = userChats.size();
                type_game = FULL_ONLINE;
                // countRounds и count_cards_on_hands уже имеют дефолты
                finishSetting = true;
                beginGame();
            }
        }
    }

    private void finishGame() {
        sendTextToAll("Конец игры");
        for (DetectiveUserChat user : userChats) {
            user.setGame(null);
        }
        bot.removeGame(this);
    }

    public boolean addPlayer(org.mikhan808.core.UserChat lobbyUser) {
        if (userChats.size() < countPlayers) {
            lobbyUser.setGame(this);
            // Создаем игрового пользователя и переводим его в режим ввода имени
            DetectiveUserChat du = new DetectiveUserChat(lobbyUser.getId(), lobbyUser.getName());
            du.setStatus(DetectiveUserChat.ENTER_NAME);
            userChats.add(du);
            // Сообщения для игрока
            bot.sendText(lobbyUser.getId(), "Добро пожаловать");
            bot.sendText(lobbyUser.getId(), "Пожалуйста введите своё имя");
            return true;
        } else {
            bot.sendText(lobbyUser.getId(), "Количество игроков уже собрано, попробуйте присоединиться к другой игре");
        }
        return false;
    }

    private void sendTextToAll(String text) {
        for (DetectiveUserChat user : userChats) {
            bot.sendText(user.getId(), text);
        }
    }

    private void forwardMsgToAll(Message message) {
        for (DetectiveUserChat user : userChats) {
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
        DetectiveUserChat[] usersScore = sortedUsers();
        for (int i = 0; i < usersScore.length; i++) {
            sb.append(i + 1).append(". ").append(usersScore[i].getName()).append(" = ").append(usersScore[i].getScore());
            sb.append(" (").append(usersScore[i].getCurrentRoundScore()).append(" -").append(usersScore[i].getDeductedPoints()).append(")\n");
        }
        return sb.toString();
    }

    private DetectiveUserChat[] sortedUsers() {
        DetectiveUserChat[] usersScore = new DetectiveUserChat[userChats.size()];
        usersScore = userChats.toArray(usersScore);
        for (int i = 0; i < usersScore.length; i++)
            for (int g = 0; g < usersScore.length - 1; g++) {
                if (usersScore[g + 1].getScore() > usersScore[g].getScore()) {
                    DetectiveUserChat temp = usersScore[g];
                    usersScore[g] = usersScore[g + 1];
                    usersScore[g + 1] = temp;
                }
            }
        return usersScore;
    }

    private void nextRound() {
        DetectiveUserChat activePlayer = getActivePlayer();
        activePlayer.setStatus(DetectiveUserChat.ACTIVE_PLAYER);
        for (DetectiveUserChat player : userChats) {
            player.resetTable();
            for (int i = 0; i < count_card_on_round; i++)
                player.addCard(getNextCard());
            bot.sendText(player.getId(), "Следующий раунд");
            bot.sendText(player.getId(), activePlayer.getName() + " - активный игрок");
            bot.sendKeyBoard(player.getId(), "Ожидаем ассоциацию", player.getCards());
        }
    }

    private void requestConfirmation(DetectiveUserChat user, int status) {
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

    private void sendCard(DetectiveUserChat user) {
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
        bot.sendKeyBoard(user.getId(), "Готово", user.getCards());
        sendTextToAll(sb.toString());
        int ind = userChats.indexOf(user);
        ind++;
        if (ind >= userChats.size())
            ind = 0;
        user.setStatus(DetectiveUserChat.OK);
        userChats.get(ind).setStatus(DetectiveUserChat.ACTIVE_PLAYER_X);
        if (userChats.get(ind).getTable().size() >= count_card_on_round) {
            runVote();
        } else {
            sendTextToAll(userChats.get(ind).getName() + " кладет карточку");
            bot.sendKeyBoard(userChats.get(ind).getId(), "Выберите слово", userChats.get(ind).getCards());
        }
    }

    private void checkVote(Message msg, DetectiveUserChat user) {
        DetectiveUserChat voteUser = findUser(msg.getText());
        if (voteUser == null)
            bot.sendText(user.getId(), "Пожалуйста выберите игрока с помощью кнопок");
        else {
            user.setVoteUser(voteUser);
            requestConfirmation(user, DetectiveUserChat.VOTE_X);
        }
    }

    private void checkCard(Message msg, DetectiveUserChat user) {
        card = "";
        for (String c : user.getCards()) {
            if (c.equals(msg.getText()))
                card = c;
        }
        if (!card.isEmpty()) {
            requestConfirmation(user, DetectiveUserChat.ACTIVE_PLAYER_Z);
        } else {
            bot.sendText(user.getId(), "Воспользуйтесь кнопками бота для отправки слова");
        }
    }

    private void sendAssociate(Message msg, DetectiveUserChat user) {
        associate = msg;
        conspiratorIndex = rand.nextInt(userChats.size());
        while (conspiratorIndex == indexActivePlayer)
            conspiratorIndex = rand.nextInt(userChats.size());
        getConspirator().setStatus(DetectiveUserChat.CONSPIRATOR);
        for (DetectiveUserChat player : userChats) {
            if (player.getStatus() == DetectiveUserChat.CONSPIRATOR)
                bot.sendText(player.getId(), "Вы конспиратор");
            else bot.forwardMessage(player.getId(), associate);
        }
        if (type_game == FULL_ONLINE) {
            user.setStatus(DetectiveUserChat.ACTIVE_PLAYER_X);
            bot.sendKeyBoard(user.getId(), "Отправьте первое слово", user.getCards());
        } else {
            sendButtonBeginDiscussion(user);
        }
    }

    private void sendButtonBeginDiscussion(DetectiveUserChat user) {
        user.setStatus(DetectiveUserChat.ACTIVE_PLAYER_X);
        List<String> buttons = new ArrayList<>();
        buttons.add(BEGIN_DISCUSSION);
        bot.sendKeyBoard(user.getId(), "Нажмите на кнопку, когда все положат по 2 карточки", buttons);
    }

    private void enterCountPlayers(Message msg, DetectiveUserChat user) {
        if (msg.getText() != null)
            try {
                int x = Integer.parseInt(msg.getText().trim());
                if (x >= MIN_COUNT_PLAYERS) {
                    countPlayers = x;
                    user.setStatus(DetectiveUserChat.ENTER_TYPE_GAME);
                    bot.sendKeyBoard(user.getId(), "Использовать онлайн карточки?", yesno);
                } else {
                    bot.sendText(user.getId(), "Количество игроков не может быть меньше " + MIN_COUNT_PLAYERS);
                }
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterTypeGame(Message msg, DetectiveUserChat user) {
        if (msg.getText() != null)
            try {
                if (msg.getText().equals(YES))
                    type_game = FULL_ONLINE;
                else type_game = WITHOUT_CARDS;
                user.setStatus(DetectiveUserChat.ENTER_COUNT_ROUNDS);
                bot.sendText(user.getId(), "Введите количество раундов");
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterCountRounds(Message msg, DetectiveUserChat user) {
        if (msg.getText() != null)
            try {
                countRounds = Integer.parseInt(msg.getText());
                user.setStatus(DetectiveUserChat.ENTER_COUNT_CARDS);
                bot.sendText(user.getId(), "Введите количество карточек на руках");
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterCountCards(Message msg, DetectiveUserChat user) {
        if (msg.getText() != null)
            try {
                count_cards_on_hands = Integer.parseInt(msg.getText());
                user.setStatus(DetectiveUserChat.OK);
                bot.sendText(user.getId(), "Ожидаем подключения всех игроков");
                finishSetting = true;
                if (enterNames == countPlayers)
                    beginGame();
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterName(Message msg, DetectiveUserChat user) {
        Long id = user.getId();
        String name = msg.getText();
        DetectiveUserChat userForName = findUser(name);
        if (userForName == null || userForName == user) {
            user.setName(name);
            enterNames++;
            if (userChats.indexOf(user) == 0) {
                user.setStatus(DetectiveUserChat.ENTER_COUNT_PLAYERS);
                bot.sendText(id, "Введите количество игроков");
            } else {
                user.setStatus(DetectiveUserChat.OK);
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
        for (DetectiveUserChat player : userChats) {
            player.getCards().clear();
            player.setScore(0);
            for (int i = 0; i < count_cards_on_hands - count_card_on_round; i++) {
                player.addCard(getNextCard());
            }
        }
        nextRound();
    }

    private DetectiveUserChat getConspirator() {
        return userChats.get(conspiratorIndex);
    }

    private DetectiveUserChat getActivePlayer() {
        return userChats.get(indexActivePlayer);
    }

    private List<String> calculateButtonsForVote(DetectiveUserChat user) {
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
        for (DetectiveUserChat player : userChats) {
            player.resetVotes();
            player.setGuessed(false);
            if (player.getStatus() == DetectiveUserChat.ACTIVE_PLAYER_X) {
                player.setStatus(DetectiveUserChat.OK);
                bot.sendText(player.getId(), "Объясните почему Вы положили именно эти карточки и ждите результата голосования");
            } else {
                player.setStatus(DetectiveUserChat.VOTE);
                bot.sendKeyBoard(player.getId(), "Рассскажите почему Вы положили именно эти карточки, выслушайте других участников и проголосуйте за того, кто по вашему мнению является конспиратором", calculateButtonsForVote(player));
            }
        }
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
}
