package org.mikhan808.games.decoder;

import org.mikhan808.Bot;
import org.mikhan808.core.Game;
import org.mikhan808.core.UserChat;
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

public class DecoderGame extends Game {
    public static final int MIN_COUNT_PLAYERS = 4;
    public static final int MIN_COUNT_TEAMS = 2;
    public static final int WIN_CARDS = 2;
    private static final String WORDS_RESOURCE_NAME = "Words.csv";

    private final Random rand;
    private final List<DecoderUserChat> userChats;
    private final List<Team> teams;
    private final List<String> yesno;

    private Stack<String> cards;
    private int countPlayers = 1000;
    private int count_cards_on_hands = 4;
    private int countVotePlayers = 0;
    private int countTeams = 2;
    private int indexActiveTeam = 0;
    private int[] code;

    public DecoderGame(Bot bot) {
        super(bot);
        userChats = new ArrayList<>();
        teams = new ArrayList<>();
        rand = new Random();
        cards = getCards();
        Collections.shuffle(cards, rand);
        yesno = new ArrayList<>();
        yesno.add(YES);
        yesno.add(NO);
    }

    private DecoderUserChat findUser(Long id) {
        for (DecoderUserChat user : userChats) {
            if (user.getId().equals(id))
                return user;
        }
        return null;
    }

    private DecoderUserChat findUser(String name) {
        for (DecoderUserChat user : userChats) {
            if (name.trim().equalsIgnoreCase(user.getName()))
                return user;
        }
        return null;
    }

    private Team findTeam(String name) {
        for (Team team : teams) {
            if (team.getName() != null && name.trim().equalsIgnoreCase(team.getName()))
                return team;
        }
        return null;
    }

    @Override
    public void readMsg(Update update) {
        Message msg = update.getMessage();
        Long id = msg.getChatId();
        DecoderUserChat user = findUser(id);
        if (user != null) {
            if (user.getStatus() == DecoderUserChat.ENTER_NAME) {
                enterName(msg, user);
            } else if (user.getStatus() == DecoderUserChat.ENTER_COUNT_PLAYERS) {
                enterCountPlayers(msg, user);
            } else if (user.getStatus() == DecoderUserChat.ENTER_COUNT_TEAMS) {
                enterCountTeams(msg, user);
            } else if (user.getStatus() == DecoderUserChat.ENTER_COUNT_CARDS) {
                enterCountCards(msg, user);
            } else if (user.getStatus() == DecoderUserChat.ENTER_NAME_TEAM) {
                enterTeamName(msg, user);
            } else if (user.getStatus() == DecoderUserChat.OK) {
                bot.sendText(id, "Ожидайте, игра настраивается");
            } else if (user.getStatus() == DecoderUserChat.ACTIVE_PLAYER) {
                if (msg.hasText()) {
                    try {
                        int x = Integer.parseInt(msg.getText().trim());
                        if (x >= 2 && x <= user.getTeam().getCards().size() - 1) {
                            code = new int[x];
                            for (int i = 0; i < x; i++) {
                                int v;
                                do {
                                    v = rand.nextInt(user.getTeam().getCards().size());
                                    boolean dup = false;
                                    for (int j = 0; j < i; j++)
                                        if (code[j] == v) {
                                            dup = true;
                                            break;
                                        }
                                    if (!dup) break;
                                } while (true);
                                code[i] = v;
                            }
                            bot.sendText(id, "Вы выбрали длину кода = " + x);
                            user.setStatus(DecoderUserChat.ACTIVE_PLAYER_X);
                            bot.sendText(id, codeToString());
                            bot.sendText(id, "Отправьте ассоциации на каждую карту по порядку");
                            getActiveTeam().getCurrentAssociates().clear();
                            requestAssociate();
                        } else bot.sendText(id, "Число вне допустимого диапазона");
                    } catch (Exception e) {
                        e.printStackTrace();
                        bot.sendText(id, "Нужно число (2..N-1)");
                    }
                }
            } else if (user.getStatus() == DecoderUserChat.ACTIVE_PLAYER_X) {
                getActiveTeam().getCurrentAssociates().put(getActiveTeam().getCards().get(code[user.indexCode]), msg);
                if (user.indexCode < code.length - 1) {
                    requestAssociate();
                } else {
                    user.setStatus(DecoderUserChat.OK);
                    bot.sendText(id, "Ассоциации приняты. Запускаем голосование...");
                    runVote(user);
                }
            } else if (user.getStatus() == DecoderUserChat.VOTE) {
                if (msg.hasText() && user.getTeam().removeVote(msg.getText())) {
                    user.setStatus(DecoderUserChat.OK);
                    String s = "Ваши голоса: " + user.getTeam().getResultVotesToString();
                    bot.sendText(id, s);
                } else {
                    bot.sendKeyBoard(id, "Выберите варианты голосования", user.getTeam().getVotes());
                }
            } else if (user.getStatus() == DecoderUserChat.VOTE_X) {
                if (YES.equals(msg.getText())) {
                    countVotePlayers++;
                    if (countVotePlayers < userChats.size()) {
                        user.setStatus(DecoderUserChat.OK);
                        bot.sendText(id, "Ваш голос учтён");
                    } else {
                        user.setStatus(DecoderUserChat.OK);
                        bot.sendText(id, "Голоса учтены");
                        showVoteResultAndContinue();
                    }
                } else {
                    user.setStatus(DecoderUserChat.VOTE);
                    String s = "Выберите варианты голосования";
                    bot.sendKeyBoard(id, s, user.getTeam().getVotes());
                }
            }
        } else {
            bot.sendText(id, "Вы не в игре. Создайте/войдите через меню.");
            UserChat lobby = bot.findUser(id);
            if (lobby != null) lobby.setGame(null);
        }
    }

    private void showVoteResultAndContinue() {
        StringBuilder sb = new StringBuilder();
        sb.append("Код = ").append(codeToString()).append("\n");
        for (Team team : teams) {
            boolean guessed = true;
            for (int i = 0; i < team.getResultVotes().size(); i++) {
                if (code[i] + 1 != Integer.parseInt(team.getResultVotes().get(i))) {
                    guessed = false;
                    break;
                }
            }
            if (guessed && team != getActiveTeam() && !getActiveTeam().isFirstTurn()) team.winCards++;
            else if (!guessed && team == getActiveTeam()) team.loseCards++;
            sb.append(team.getName()).append(" = ").append(team.getResultVotesToString()).append("\n");
        }
        sendTextToAll(sb.toString());
        getActiveTeam().setFirstTurn(false);
        boolean end = false;
        for (Team team : teams) {
            if (team.winCards == WIN_CARDS) {
                end = true;
                break;
            }
        }
        if (end) finishGame();
        else {
            getActiveTeam().incIndexActivePlayer();
            for (String key : getActiveTeam().getAssociates().keySet()) {
                Message m = getActiveTeam().getCurrentAssociates().get(key);
                if (m != null) getActiveTeam().getAssociates().get(key).add(m);
            }
            indexActiveTeam++;
            if (indexActiveTeam >= teams.size()) indexActiveTeam = 0;
            nextRound();
        }
    }

    private void finishGame() {
        StringBuilder sb = new StringBuilder();
        for (Team team : teams) {
            sb.append(team.getName()).append("\n");
            sb.append(team.getCardsToString());
        }
        sendTextToAll(sb.toString());
        for (DecoderUserChat u : userChats) u.setGame(null);
        bot.removeGame(this);
    }

    @Override
    public boolean addPlayer(UserChat lobbyUser) {
        if (userChats.size() < countPlayers) {
            lobbyUser.setStatus(DecoderUserChat.ENTER_NAME);
            lobbyUser.setGame(this);
            bot.sendText(lobbyUser.getId(), "Введите имя");
            bot.sendText(lobbyUser.getId(), "Сколько игроков будет в игре?");
            DecoderUserChat du = new DecoderUserChat(lobbyUser.getId(), lobbyUser.getName());
            du.setStatus(DecoderUserChat.ENTER_NAME);
            userChats.add(du);
            return true;
        } else bot.sendText(lobbyUser.getId(), "Слишком много игроков. Вернитесь в меню.");
        return false;
    }

    private void sendTextToAll(String text) {
        for (DecoderUserChat u : userChats) bot.sendText(u.getId(), text);
    }

    private void forwardMsgToAll(Message message) {
        for (DecoderUserChat u : userChats) bot.forwardMessage(u.getId(), message);
    }

    private boolean finishedSettings() {
        for (Team t : teams) if (t.getName() == null) return false;
        return teams.size() >= countTeams;
    }

    private void nextRound() {
        DecoderUserChat activePlayer = getActivePlayer();
        activePlayer.setStatus(DecoderUserChat.ACTIVE_PLAYER);
        sendTextToAll("Новый раунд!");
        sendTextToAll(getActiveTeam().getName() + " — активная команда");
        sendTextToAll(activePlayer.getName() + " — активный игрок");
        sendTeam(getActiveTeam(), "Ваши карты:");
        sendTeam(getActiveTeam(), getActiveTeam().getCardsToString());
        sendTextToAll("Остальные команды ждут ассоциации");
        bot.sendText(activePlayer.getId(), "Выберите длину кода (2..N-1) и отправьте");
        getActiveTeam().getCurrentAssociates().clear();
        for (Team team : teams) team.resetVotes();
    }

    private void sendTeamExcludeActivePlayer(Team team, String text) {
        for (DecoderUserChat u : team.getPlayers()) if (u != getActivePlayer()) bot.sendText(u.getId(), text);
    }

    private void sendTeam(Team team, String text) {
        for (DecoderUserChat u : team.getPlayers()) bot.sendText(u.getId(), text);
    }

    private void sendTeamExcludeActivePlayer(Team team, Message msg) {
        for (DecoderUserChat u : team.getPlayers()) if (u != getActivePlayer()) bot.forwardMessage(u.getId(), msg);
    }

    private void sendTeamExcludeActivePlayerAndUser(Team team, Message msg, DecoderUserChat userChat) {
        for (DecoderUserChat u : team.getPlayers())
            if (u != getActivePlayer() && u != userChat) bot.forwardMessage(u.getId(), msg);
    }

    private void sendTeamExcludeActivePlayerAndUser(Team team, String text, DecoderUserChat userChat) {
        for (DecoderUserChat u : team.getPlayers())
            if (u != getActivePlayer() && u != userChat) bot.sendText(u.getId(), text);
    }

    private void requestAssociate() {
        // next associate for active player
        DecoderUserChat ap = getActivePlayer();
        ap.indexCode++;
        bot.sendText(ap.getId(), "Введите ассоциацию к карте '" + getActiveTeam().getCards().get(code[ap.indexCode]) + "'");
    }

    private String codeToString() {
        StringBuilder x = new StringBuilder();
        for (int i = 0; i < code.length; i++) {
            if (i != 0) x.append(", ");
            x.append(code[i] + 1);
        }
        return x.toString();
    }

    private String getNextCard() {
        if (cards.empty()) {
            cards = getCards();
            Collections.shuffle(cards, rand);
        }
        return cards.pop();
    }

    private void runVote(DecoderUserChat active) {
        for (DecoderUserChat u : active.getTeam().getPlayers()) u.setStatus(DecoderUserChat.OK);
        for (Team team : teams) team.resetVotes();
        StringBuilder sb = new StringBuilder();
        sb.append("Ассоциации в разном порядке: \n");
        for (int i = 0; i < getActiveTeam().getCards().size(); i++) {
            bot.sendText(active.getId(), "Карта " + (i + 1));
            List<Message> msgs = getActiveTeam().getAssociates().get(getActiveTeam().getCards().get(i));
            for (Message m : msgs) forwardMsgToAll(m);
        }
        sendTextToAll("А теперь ассоциации активной команды:");
        for (int i = 0; i < code.length; i++)
            forwardMsgToAll(getActiveTeam().getCurrentAssociates().get(getActiveTeam().getCards().get(code[i])));
        sendTextToAll("Готовы голосовать? Отправьте /ready");
        for (DecoderUserChat u : userChats) {
            if (u.getTeam() != getActiveTeam()) {
                u.setStatus(DecoderUserChat.VOTE);
                String s = "Введите ваши голоса по порядку";
                bot.sendKeyBoard(u.getId(), s, u.getTeam().getVotes());
            }
        }
        countVotePlayers = 0;
    }

    private void enterName(Message msg, DecoderUserChat user) {
        Long id = user.getId();
        String name = msg.getText();
        DecoderUserChat userForName = findUser(name);
        if (userForName == null) {
            user.setName(name);
            bot.sendText(id, "Сколько команд? (минимум " + MIN_COUNT_TEAMS + ")");
            user.setStatus(DecoderUserChat.ENTER_COUNT_TEAMS);
        } else {
            bot.sendText(id, "Имя занято, введите другое");
        }
    }

    private void enterCountPlayers(Message msg, DecoderUserChat user) {
        Long id = user.getId();
        try {
            countPlayers = Integer.parseInt(msg.getText().trim());
            bot.sendText(id, "Ок. Игроков будет: " + countPlayers);
            bot.sendText(id, "Сколько карт на руках? (по умолчанию " + count_cards_on_hands + ")");
            user.setStatus(DecoderUserChat.ENTER_COUNT_CARDS);
        } catch (Exception e) {
            e.printStackTrace();
            bot.sendText(id, "Введите число");
            user.setStatus(DecoderUserChat.ENTER_COUNT_PLAYERS);
        }
    }

    private void enterCountTeams(Message msg, DecoderUserChat user) {
        Long id = user.getId();
        try {
            countTeams = Math.max(MIN_COUNT_TEAMS, Integer.parseInt(msg.getText().trim()));
            bot.sendText(id, "Ок. Команд будет: " + countTeams);
            bot.sendText(id, "Введите название вашей команды");
            user.setStatus(DecoderUserChat.ENTER_NAME_TEAM);
            createTeam(user);
        } catch (Exception e) {
            e.printStackTrace();
            bot.sendText(id, "Введите число");
            user.setStatus(DecoderUserChat.ENTER_COUNT_TEAMS);
        }
    }

    private void enterTeamName(Message msg, DecoderUserChat user) {
        Long id = user.getId();
        String name = msg.getText();
        Team t = findTeam(name);
        if (t == null) {
            user.getTeam().setName(name);
            checkFinishSettingsForBeginGame(user);
        } else {
            bot.sendText(id, "Имя занято. Введите другое имя команды");
        }
    }

    private void enterCountCards(Message msg, DecoderUserChat user) {
        Long id = user.getId();
        try {
            count_cards_on_hands = Integer.parseInt(msg.getText().trim());
            bot.sendText(id, "Ок. Карт на руках: " + count_cards_on_hands);
            bot.sendText(id, "Сколько команд? (минимум " + MIN_COUNT_TEAMS + ")");
            user.setStatus(DecoderUserChat.ENTER_COUNT_TEAMS);
        } catch (Exception e) {
            e.printStackTrace();
            bot.sendText(id, "Введите число");
            user.setStatus(DecoderUserChat.ENTER_COUNT_CARDS);
        }
    }

    private void createTeam(DecoderUserChat user) {
        Team team = new Team();
        teams.add(team);
        team.addPlayer(user);
    }

    private void checkFinishSettingsForBeginGame(DecoderUserChat user) {
        if (finishedSettings()) {
            beginGame();
        } else {
            int ind = userChats.indexOf(user) % countTeams;
            teams.get(ind).addPlayer(user);
            user.setStatus(DecoderUserChat.OK);
            bot.sendText(user.getId(), "Вы добавлены в команду. Ожидайте начала");
        }
    }

    private void beginGame() {
        sendTextToAll("Начинаем игру!");
        for (Team team : teams) {
            team.getCards().clear();
            for (int i = 0; i < count_cards_on_hands; i++) team.addCard(getNextCard());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Состав команд:\n");
        for (Team team : teams) {
            sb.append("Команда ").append(team.getName()).append(":\n");
            for (DecoderUserChat u : team.getPlayers()) sb.append(" - ").append(u.getName()).append("\n");
        }
        sendTextToAll(sb.toString());
        indexActiveTeam = 0;
        nextRound();
    }

    private Team getActiveTeam() {
        return teams.get(indexActiveTeam);
    }

    private DecoderUserChat getActivePlayer() {
        return getActiveTeam().getActivePlayer();
    }

    private Stack<String> getCards() {
        Stack<String> result = new Stack<>();
        try {
            InputStream is = DecoderGame.class.getClassLoader().getResourceAsStream(WORDS_RESOURCE_NAME);
            if (is == null) throw new IOException("Resource not found: " + WORDS_RESOURCE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty()) result.push(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
