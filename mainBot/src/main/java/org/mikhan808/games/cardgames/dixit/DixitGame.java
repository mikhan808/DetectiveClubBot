package org.mikhan808.games.cardgames.dixit;

import org.mikhan808.Bot;
import org.mikhan808.core.UserChat;
import org.mikhan808.games.cardgames.AbstractAssociateCardGame;
import org.mikhan808.games.cardgames.AbstractCardsGame;
import org.mikhan808.games.cardgames.decoder.DecoderUserChat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

import static org.mikhan808.Bot.NO;
import static org.mikhan808.Bot.YES;

public class DixitGame extends AbstractAssociateCardGame {
    public static final String NAME="Диксит";
    private final int all_guessed_score = 2;
    private final int adding_score = 1;
    private final List<String> table;
    public DixitGame(Bot bot) {
        super(bot);
        table = new ArrayList<>();
    }

    @Override
    public void processMsg(Update update) {
        Message msg = update.getMessage();
        Long id = msg.getChatId();
        DixitUserChat user = (DixitUserChat) findUser(id);
        if (user != null) {
            if (user.getStatus() == DixitUserChat.ENTER_COUNT_ROUNDS) {
                enterCountRounds(msg, user);
            } else if (user.getStatus() == DixitUserChat.ENTER_COUNT_CARDS) {
                enterCountCards(msg, user);
            } else if (user.getStatus() == DixitUserChat.OK) {
                bot.sendText(id, "Ожидаем, потерпите)");
            } else if (user.getStatus() == DixitUserChat.ACTIVE_PLAYER) {
                sendAssociate(msg, user);
            } else if (user.getStatus() == DixitUserChat.ACTIVE_PLAYER_X) {
                checkCard(msg, user);
            } else if (user.getStatus() == DixitUserChat.VOTE) {
                checkVote(msg, user);
            } else if (user.getStatus() == DixitUserChat.ACTIVE_PLAYER_Z) {
                if (msg.getText().equals(YES)) {
                    sendCard(user);
                } else {
                    user.setStatus(DixitUserChat.ACTIVE_PLAYER_X);
                    bot.sendKeyBoard(user.getId(), "Выберите карточку", user.getCards());
                }
            } else if (user.getStatus() == DixitUserChat.VOTE_X) {
                if (msg.getText().equals(YES)) {
                    user.getVoteUser().incVotes();
                    user.setGuessed(user.getVoteUser() == getActivePlayer());
                    countVotePlayers++;
                    if (countVotePlayers < players.size() - 1) {
                        user.setStatus(UserChat.OK);
                        bot.sendKeyBoard(id, "Ожидайте других игроков", user.getCards());
                    } else {
                        user.setStatus(UserChat.OK);
                        bot.sendKeyBoard(id, "Ожидайте других игроков", user.getCards());
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
                    user.setStatus(DixitUserChat.VOTE);
                    bot.sendKeyBoard(id, "Проголосуйте еще раз", table);
                }

            }
        }
    }

    @Override
    protected UserChat createUserChat(UserChat lobbyUserChat) {
        return new DixitUserChat(lobbyUserChat.getId(),lobbyUserChat.getName());
    }

    @Override
    protected void afterEnterCountPlayers(UserChat user) {
        user.setStatus(DixitUserChat.ENTER_COUNT_ROUNDS);
        bot.sendText(user.getId(), "Введите количество раундов");
    }

    @Override
    public int getMinCountPlayers() {
        return 4;
    }



    private DixitUserChat findUserOfCard(String card) {

        for (UserChat u : players) {
            DixitUserChat user = (DixitUserChat) u;
            if (card.trim().equalsIgnoreCase(user.getCardOnTable()))
                return user;
        }
        return null;
    }

    private void calculateScores() {
        boolean all_guessed = false;
        if (getActivePlayer().getVotes() > 0 && getActivePlayer().getVotes() < players.size() - 1) {
            getActivePlayer().setCurrentRoundScore(active_player_score);
        } else if (getActivePlayer().getVotes() == players.size() - 1) {
            getActivePlayer().setCurrentRoundScore(0);
            all_guessed = true;
        } else {
            getActivePlayer().setCurrentRoundScore(0);
        }
        for (int i = 0; i < players.size(); i++) {
            DixitUserChat user = (DixitUserChat) players.get(i);
            if (i != indexActivePlayer) {
                if (user.isGuessed()) {
                    if (all_guessed)
                        user.setCurrentRoundScore(all_guessed_score);
                    else user.setCurrentRoundScore(guessed_score);
                } else
                    user.setCurrentRoundScore(0);
                user.setAddingScore(user.getVotes() * adding_score);
            } else {
                user.setAddingScore(0);
            }
        }
    }

    private String buildListUsersScore() {
        StringBuilder sb = new StringBuilder();
        sb.append("Карта рассказчика - ").append(getActivePlayer().getCardOnTable()).append("\n");
        DixitUserChat[] usersScore = sortedUsers();
        for (int i = 0; i < usersScore.length; i++) {
            sb.append(i + 1).append(". ").append(usersScore[i].getName()).append(" '").append(usersScore[i].getCardOnTable()).append("' = ").append(usersScore[i].getScore());
            sb.append(" (").append(usersScore[i].getCurrentRoundScore()).append(" +").append(usersScore[i].getAddingScore()).append(")\n");
        }
        return sb.toString();
    }

    private DixitUserChat[] sortedUsers() {
        DixitUserChat[] usersScore = new DixitUserChat[players.size()];
        usersScore = players.toArray(usersScore);
        for (int i = 0; i < usersScore.length; i++)
            for (int g = 0; g < usersScore.length - 1; g++) {
                if (usersScore[g + 1].getScore() > usersScore[g].getScore()) {
                    DixitUserChat temp = usersScore[g];
                    usersScore[g] = usersScore[g + 1];
                    usersScore[g + 1] = temp;
                }
            }
        return usersScore;
    }

    private void nextRound() {
        DixitUserChat activePlayer = getActivePlayer();
        table.clear();
        for (UserChat user : players) {
            DixitUserChat userChat = (DixitUserChat) user;
            for (int i = 0; i < count_card_on_round; i++) {
                userChat.addCard(getNextCard());
                userChat.setStatus(UserChat.OK);
            }
            bot.sendText(userChat.getId(), "Следующий раунд");
            bot.sendText(userChat.getId(), activePlayer.getName() + " - активный игрок");
            bot.sendKeyBoard(userChat.getId(), "Ожидаем ассоциацию", userChat.getCards());
        }
        activePlayer.setStatus(UserChat.ACTIVE_PLAYER);
    }

    private void requestConfirmation(UserChat user, int status) {
        user.setStatus(status);
        bot.sendKeyBoard(user.getId(), "Вы уверены?", yesno);
    }

    private void sendCard(DixitUserChat user) {
        user.moveCardToTable(user.getCardOnTable());
        table.add(user.getCardOnTable());
        user.setStatus(UserChat.OK);
        bot.sendKeyBoard(user.getId(), "Ок, ожидаем других игроков", user.getCards());
        if (table.size() == players.size())
            runVote();
    }

    private void checkVote(Message msg, DixitUserChat user) {
        DixitUserChat voteUser = findUserOfCard(msg.getText());
        if (voteUser == null)
            bot.sendKeyBoard(user.getId(), "Пожалуйста проголосуйте с помощью кнопок", table);
        else if (voteUser == user) {
            bot.sendKeyBoard(user.getId(), "За себя голосовать нельзя", table);
        } else {
            user.setVoteUser(voteUser);
            requestConfirmation(user, DixitUserChat.VOTE_X);
        }
    }

    private void checkCard(Message msg, DixitUserChat user) {
        String card = "";
        for (String c : user.getCards()) {
            if (c.equals(msg.getText()))
                card = c;
        }
        if (!card.isEmpty()) {
            user.setCardOnTable(card);
            requestConfirmation(user, DixitUserChat.ACTIVE_PLAYER_Z);
        } else {
            bot.sendText(user.getId(), "Воспользуйтесь кнопками бота для отправки слова");
        }
    }

    private void sendAssociate(Message msg, UserChat user) {
        associate = msg;
        for (UserChat u : players) {
            DixitUserChat player = (DixitUserChat)u;
            bot.forwardMessage(player.getId(), associate);
            player.setStatus(DixitUserChat.ACTIVE_PLAYER_X);
            bot.sendKeyBoard(player.getId(), "Отправьте подходящую карточку", player.getCards());
        }

    }


    private void enterCountRounds(Message msg, UserChat user) {

        if (msg.getText() != null)
            try {
                countRounds = Integer.parseInt(msg.getText());
                user.setStatus(DixitUserChat.ENTER_COUNT_CARDS);
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

    protected void beginGame() {
        sendTextToAll("Начинаем!");
        for (UserChat u : players) {
            DixitUserChat userChat = (DixitUserChat)u;
            userChat.getCards().clear();
            userChat.setScore(0);
            for (int i = 0; i < count_cards_on_hands - count_card_on_round; i++) {
                userChat.addCard(getNextCard());
            }
        }
        nextRound();


    }

    private DixitUserChat getActivePlayer() {
        return (DixitUserChat) players.get(indexActivePlayer);
    }


    private void runVote() {
        countVotePlayers = 0;
        Collections.shuffle(table, rand);
        for (UserChat user : players) {
            DixitUserChat userChat=(DixitUserChat) user;
            userChat.resetVotes();
            userChat.setGuessed(false);
            if (userChat == getActivePlayer()) {
                userChat.setStatus(UserChat.OK);
                bot.sendKeyBoard(userChat.getId(), "Ждите результата голосования", table);
            } else {
                userChat.setStatus(DixitUserChat.VOTE);
                bot.sendKeyBoard(userChat.getId(), "проголосуйте за карточку,"
                        + " которая по вашему мнению является карточкой рассказчика", table);
            }
        }
    }
}
