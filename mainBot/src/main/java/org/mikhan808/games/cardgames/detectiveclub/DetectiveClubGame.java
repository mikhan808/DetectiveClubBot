package org.mikhan808.games.cardgames.detectiveclub;

import org.mikhan808.Bot;
import org.mikhan808.core.UserChat;
import org.mikhan808.games.cardgames.AbstractAssociateCardGame;
import org.mikhan808.games.cardgames.AbstractCardsGame;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

import static org.mikhan808.Bot.NO;
import static org.mikhan808.Bot.YES;

public class DetectiveClubGame extends AbstractAssociateCardGame {
    public static final String NAME="Конспиратор";
    public static final int USER_SCORING = 1;

    public static final int FULL_ONLINE = 0;
    public static final int WITHOUT_CARDS = 1;
    private static final String BEGIN_DISCUSSION = "Перейти к обсуждению";
    private final int conspirator_score = 3;
    private final int deducted_score = 1;
    private final int type_scoring = USER_SCORING;
    private int conspiratorIndex = 0;
    private String card;
    private int type_game = FULL_ONLINE;

    public DetectiveClubGame(Bot bot) {
        super(bot);
        count_card_on_round = 2;
    }

    public void processMsg(Update update) {
        Message msg = update.getMessage();
        Long id = msg.getChatId();
        DetectiveUserChat user = (DetectiveUserChat) findUser(id);
        if (user != null) {
            if (user.getStatus() == DetectiveUserChat.ENTER_TYPE_GAME) {
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
                    if (players.indexOf(user) != conspiratorIndex)
                        user.getVoteUser().incVotes();
                    user.setGuessed(players.indexOf(user.getVoteUser()) == conspiratorIndex);
                    countVotePlayers++;
                    if (countVotePlayers < players.size() - 1) {
                        user.setStatus(DetectiveUserChat.OK);
                        bot.sendText(id, "Ожидайте других игроков");
                    } else {
                        user.setStatus(DetectiveUserChat.OK);
                        bot.sendText(id, "Ожидайте других игроков");
                        calculateScores();
                        String usersScore = buildListUsersScore();
                        sendTextToAll(usersScore);
                        boolean end = false;
                        if (indexActivePlayer < players.size() - 1)
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
            for (UserChat u : players) if (u.getName() != null && !u.getName().trim().isEmpty()) named++;
            if (players.size() >= getMinCountPlayers() && named == players.size()) {
                countPlayers = players.size();
                type_game = FULL_ONLINE;
                // countRounds и count_cards_on_hands уже имеют дефолты
                finishSetting = true;
                beginGame();
            }
        }
    }

    @Override
    public int getMinCountPlayers() {
        return 4;
    }
    @Override
    protected UserChat createUserChat(UserChat lobbyUserChat) {
        return new DetectiveUserChat(lobbyUserChat.getId(),lobbyUserChat.getName());
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
        for (int i = 0; i < players.size(); i++) {
            DetectiveUserChat u = (DetectiveUserChat) players.get(i);
            if (i != conspiratorIndex && i != indexActivePlayer) {
                if (u.isGuessed())
                    u.setCurrentRoundScore(guessed_score);
                else
                    u.setCurrentRoundScore(0);
                u.setDeductedPoints(u.getVotes() * deducted_score);
            } else {
               u.setDeductedPoints(0);
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
        DetectiveUserChat[] usersScore = new DetectiveUserChat[players.size()];
        usersScore = players.toArray(usersScore);
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
        for (UserChat u : players) {
            DetectiveUserChat player = (DetectiveUserChat) u;
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

    private void sendCard(DetectiveUserChat user) {
        StringBuilder sb = new StringBuilder();
        user.moveCardToTable(card);
        for (int i = indexActivePlayer, g = 0; g < players.size(); g++) {
            DetectiveUserChat du = (DetectiveUserChat) players.get(i);
            if (du.getTable().size() > 0) {
                sb.append(du.getName());
                sb.append(" (");
                for (int x = 0; x < du.getTable().size(); x++) {
                    if (x != 0)
                        sb.append(", ");
                    sb.append(du.getTable().get(x));
                }
                sb.append(")\n");
            }
            i++;
            if (i >= players.size())
                i = 0;
        }
        bot.sendKeyBoard(user.getId(), "Готово", user.getCards());
        sendTextToAll(sb.toString());
        int ind = players.indexOf(user);
        ind++;
        if (ind >= players.size())
            ind = 0;
        user.setStatus(DetectiveUserChat.OK);
        DetectiveUserChat du = (DetectiveUserChat) players.get(ind);
        du.setStatus(DetectiveUserChat.ACTIVE_PLAYER_X);
        if (du.getTable().size() >= count_card_on_round) {
            runVote();
        } else {
            sendTextToAll(du.getName() + " кладет карточку");
            bot.sendKeyBoard(du.getId(), "Выберите слово", du.getCards());
        }
    }

    private void checkVote(Message msg, DetectiveUserChat user) {
        DetectiveUserChat voteUser = (DetectiveUserChat) findUserByName(msg.getText());
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
        conspiratorIndex = rand.nextInt(players.size());
        while (conspiratorIndex == indexActivePlayer)
            conspiratorIndex = rand.nextInt(players.size());
        getConspirator().setStatus(DetectiveUserChat.CONSPIRATOR);
        for (UserChat u : players) {
            DetectiveUserChat player = (DetectiveUserChat) u;
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

    protected void afterEnterCountPlayers(UserChat user)
    {
        user.setStatus(DetectiveUserChat.ENTER_TYPE_GAME);
        bot.sendKeyBoard(user.getId(), "Использовать онлайн карточки?", yesno);
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

    protected void beginGame() {
        sendTextToAll("Начинаем!");
        for (UserChat u: players) {
            DetectiveUserChat player = (DetectiveUserChat)u;
            player.getCards().clear();
            player.setScore(0);
            for (int i = 0; i < count_cards_on_hands - count_card_on_round; i++) {
                player.addCard(getNextCard());
            }
        }
        nextRound();
    }

    private DetectiveUserChat getConspirator() {
        return(DetectiveUserChat) players.get(conspiratorIndex);
    }

    private DetectiveUserChat getActivePlayer() {
        return(DetectiveUserChat) players.get(indexActivePlayer);
    }

    private List<String> calculateButtonsForVote(DetectiveUserChat user) {
        List<String> buttons = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (i != indexActivePlayer && !players.get(i).getId().equals(user.getId())) {
                buttons.add(players.get(i).getName());
            }
        }
        return buttons;
    }

    private void runVote() {
        countVotePlayers = 0;
        sendTextToAll("Ассоциация:");
        copyMsgToAll(associate);
        for (UserChat u : players) {
            DetectiveUserChat player=(DetectiveUserChat)u;
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
}
