package org.mikhan808;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.mikhan808.Bot.NO;
import static org.mikhan808.Bot.YES;

public class Game {
    public static final int MIN_COUNT_PLAYERS = 4;
    public static final int MIN_COUNT_TEAMS = 2;
    public static final int WIN_CARDS = 2;
    private static final String WORDS_FILE_NAME = "Words.csv";
    private final Random rand;
    private final List<UserChat> userChats;
    private final Bot bot;
    private final List<String> yesno;
    int enterNames = 0;
    private int id;
    private Stack<String> cards;
    private int countPlayers = 1000;
    private final List<Team> teams;
    private int count_cards_on_hands = 4;
    private int countVotePlayers = 0;
    private int countTeams = 2;
    private int indexActiveTeam = 0;
    private int[] code;

    public Game(Bot bot) {
        this.bot = bot;
        userChats = new ArrayList<>();
        teams = new ArrayList<>();
        rand = new Random();
        cards = loadCards();
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

    private int indexCode;

    private Team findTeam(String name) {

        for (Team team : teams) {
            if (name.trim().equalsIgnoreCase(team.getName()))
                return team;
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
            } else if (user.getStatus() == UserChat.ENTER_COUNT_TEAMS) {
                enterCountTeams(msg, user);
            } else if (user.getStatus() == UserChat.ENTER_COUNT_CARDS) {
                enterCountCards(msg, user);
            } else if (user.getStatus() == UserChat.ENTER_NAME_TEAM) {
                enterTeamName(msg, user);
            } else if (user.getStatus() == UserChat.OK) {
                bot.sendText(id, "Ожидаем, потерпите)");
            } else if (user.getStatus() == UserChat.ACTIVE_PLAYER) {
                saveAssociate(msg, user);
            } else if (user.getStatus() == UserChat.VOTE) {
                checkVote(msg, user);
            } else if (user.getStatus() == UserChat.DISCUSSION) {
                if (msg.hasText() && msg.getText().equals("/ready")) {
                    runVote(user);
                } else {
                    sendTeamExcludeActivePlayerAndUser(user.getTeam(), "[" + user.getName() + "]", user);
                    sendTeamExcludeActivePlayerAndUser(user.getTeam(), msg, user);
                }
            }

        } else {
            bot.sendText(id, "Почему то вас нет в списках попробуйте присоединиться к другой игре");
            bot.findUser(id).setGame(null);
        }
    }

    void checkFinishSettingsForBeginGame(UserChat user) {
        if (finishedSettings())
            beginGame();
        else {
            bot.sendText(user.getId(), "Ожидаем подключения всех игроков");
        }
    }

    private void finishGame() {
        StringBuilder sb = new StringBuilder();
        for (Team team : teams) {
            sb.append(team.getName()).append("\n");
            sb.append(team.getCardsToString());
        }
        sendTextToAll(sb.toString());
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

    boolean finishedSettings() {
        for (Team team : teams)
            if (team.getName() == null)
                return false;
        return enterNames == countPlayers;
    }

    private void nextRound() {
        countVotePlayers = 0;
        for (UserChat userChat : userChats) {
            userChat.setStatus(UserChat.OK);
        }
        UserChat activePlayer = getActivePlayer();
        generateCode();
        indexCode = -1;
        activePlayer.setStatus(UserChat.ACTIVE_PLAYER);
        sendTextToAll("Следующий раунд");
        sendTextToAll(getActiveTeam().getName() + " - активная команда");
        sendTextToAll(activePlayer.getName() + " - активный игрок");
        sendTeam(getActiveTeam(), "Ваши карточки");
        sendTeam(getActiveTeam(), getActiveTeam().getCardsToString());
        sendTextToAll("Ожидаем список ассоциаций");
        bot.sendText(activePlayer.getId(), "Вот код, который Вы должны передать своей команде с помощью ассоциаций" +
                " Вам надо, чтоб ваша команда угадала код, а другие не догадались");
        bot.sendText(activePlayer.getId(), codeToString());
        getActiveTeam().getCurrentAssociates().clear();
        for (Team team : teams) {
            team.resetVotes();
        }
        requestAssociate();
    }

    private void sendTeamExcludeActivePlayer(Team team, String text) {
        for (UserChat user : team.userChats) {
            if (user != getActivePlayer())
                bot.sendText(user.getId(), text);
        }
    }

    private void sendTeam(Team team, String text) {
        for (UserChat user : team.userChats) {
            bot.sendText(user.getId(), text);
        }
    }

    private void sendTeamExcludeActivePlayer(Team team, Message msg) {
        for (UserChat user : team.userChats) {
            if (user != getActivePlayer())
                bot.forwardMessage(user.getId(), msg);
        }
    }

    private void sendTeamExcludeActivePlayerAndUser(Team team, Message msg, UserChat userChat) {
        for (UserChat user : team.userChats) {
            if (user != getActivePlayer() && user != userChat)
                bot.forwardMessage(user.getId(), msg);
        }
    }

    private void sendTeamExcludeActivePlayerAndUser(Team team, String text, UserChat userChat) {
        for (UserChat user : team.userChats) {
            if (user != getActivePlayer() && user != userChat)
                bot.sendText(user.getId(), text);
        }
    }

    private void requestAssociate() {
        indexCode++;
        bot.sendText(getActivePlayer().getId(), "Отправьте ассоциацию на карточку '" + getActiveTeam().getCards().get(code[indexCode]) + "'");
    }

    private String codeToString() {
        String x = "";
        for (int i = 0; i < code.length; i++) {
            if (i != 0)
                x += ", ";
            x += (code[i] + 1) + "";
        }
        return x;
    }

    private void generateCode() {
        code = new int[count_cards_on_hands - 1];
        for (int i = 0; i < code.length; i++) {
            int x = -1;
            while (x == -1) {
                x = rand.nextInt(count_cards_on_hands);
                for (int g = 0; g < i; g++) {
                    if (x == code[g]) {
                        x = -1;
                        break;
                    }

                }
            }
            code[i] = x;
        }
    }

    private void requestConfirmation(UserChat user, int status) {
        user.setStatus(status);
        bot.sendKeyBoard(user.getId(), "Вы уверены?", yesno);
    }

    private String getNextCard() {
        if (cards.empty()) {
            cards = loadCards();
            Collections.shuffle(cards, rand);
        }
        return cards.pop();
    }


    private void checkVote(Message msg, UserChat user) {
        if (countVotePlayers >= teams.size()) {
            showResult();
        } else {
            if (!user.getTeam().removeVote(msg.getText()))
                bot.sendText(user.getId(), "Пожалуйста выберите цифру с помощью кнопок");
            else {
                requestVote(user);
            }
        }
    }

    private void saveAssociate(Message msg, UserChat user) {
        // Disallow reusing the same textual clue within the same game for this team
        if (msg.hasText() && isClueTextUsed(getActiveTeam(), msg.getText())) {
            bot.sendText(user.getId(), "�?� �����?�?�? �?���?���?���?�?�?���. �������� ��������� ��������.");
            return;
        }
        getActiveTeam().getCurrentAssociates().put(getActiveTeam().getCards().get(code[indexCode]), msg);
        if (indexCode < code.length - 1) {
            requestAssociate();
        } else {
            beginDiscussion();
        }
    }

    private boolean isClueTextUsed(Team team, String text) {
        if (text == null) return false;
        for (String key : team.getAssociates().keySet()) {
            List<Message> history = team.getAssociates().get(key);
            for (Message m : history) {
                if (m != null && m.hasText() && text.equalsIgnoreCase(m.getText())) return true;
            }
        }
        for (Message m : team.getCurrentAssociates().values()) {
            if (m != null && m.hasText() && text.equalsIgnoreCase(m.getText())) return true;
        }
        return false;
    }

    private void beginDiscussion() {
        getActivePlayer().setStatus(UserChat.OK);

        sendTextToAll("Начинается дискуссия, вы можете обсудить здесь со своей командой" +
                " ваши предположения. Напоминаем, что вам нужно указать код, который загадал активный игрок." +
                "Для удобства мы вам высылаем ассоциации, которые были в предыдущих раундах");
        for (int i = 0; i < getActiveTeam().getCards().size(); i++) {
            sendTextToAll("Карточка №" + (i + 1));
            List<Message> msgs = getActiveTeam().getAssociates().get(getActiveTeam().getCards().get(i));
            for (int g = 0; g < msgs.size(); g++) {
                forwardMsgToAll(msgs.get(g));
            }
        }
        sendTextToAll("Текущие ассоциации от активного игрока");
        for (int i = 0; i < code.length; i++)
            forwardMsgToAll(getActiveTeam().getCurrentAssociates().get(getActiveTeam().getCards().get(code[i])));
        sendTextToAll("Когда вы будете готовы дать ответ,тот кто будет отвечать пусть отправит /ready");
        for (UserChat user : userChats) {
            if (user != getActivePlayer())
                user.setStatus(UserChat.DISCUSSION);
        }

    }

    private void enterCountPlayers(Message msg, UserChat user) {

        if (msg.getText() != null)
            try {
                int x = Integer.parseInt(msg.getText().trim());
                if (x >= MIN_COUNT_PLAYERS) {
                    countPlayers = x;
                    user.setStatus(UserChat.ENTER_COUNT_TEAMS);
                    bot.sendText(user.getId(), "Введите количество команд.(В каждой команде должно быть не менее 2 человек)");
                } else {
                    bot.sendText(user.getId(), "Количество игроков не может быть меньше " + MIN_COUNT_PLAYERS);
                }
            } catch (Exception e) {
                e.printStackTrace();
                bot.sendText(user.getId(), "Что-то не так. Пожалуйста проверьте данные и повторите ввод");
            }
    }

    private void enterCountTeams(Message msg, UserChat user) {

        if (msg.getText() != null)
            try {
                int x = Integer.parseInt(msg.getText().trim());
                if (x >= MIN_COUNT_TEAMS && countPlayers / x >= 2) {
                    countTeams = x;
                    user.setStatus(UserChat.ENTER_COUNT_CARDS);
                    bot.sendText(user.getId(), "Введите количество карточек на руках");
                } else {
                    bot.sendText(user.getId(), "Количество команд не может быть меньше " + MIN_COUNT_TEAMS);
                }
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
                bot.sendText(user.getId(), "№ игры = " + getId());
                createTeam(user);
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
                if (teams.size() < countTeams && userChats.indexOf(user) >= teams.size()) {
                    createTeam(user);
                } else {
                    int ind = userChats.indexOf(user) % countTeams;
                    teams.get(ind).addPlayer(user);
                    user.setStatus(UserChat.OK);
                    checkFinishSettingsForBeginGame(user);
                }

            }
        } else bot.sendText(id, "Пожалуйста введите другое имя, данное имя уже занято");
    }

    private void enterTeamName(Message msg, UserChat user) {
        Long id = user.getId();
        String name = msg.getText();
        Team teamForName = findTeam(name);
        if (teamForName == null) {
            user.getTeam().setName(name);
            checkFinishSettingsForBeginGame(user);
        } else bot.sendText(id, "Пожалуйста введите другое имя, данное имя уже занято");
    }

    private void createTeam(UserChat user) {
        Team team = new Team();
        teams.add(team);
        team.addPlayer(user);
        user.setStatus(UserChat.ENTER_NAME_TEAM);
        bot.sendText(user.getId(), "Введите название своей команды");
    }

    private void beginGame() {
        sendTextToAll("Начинаем!");
        for (Team team : teams) {
            team.getCards().clear();
            for (int i = 0; i < count_cards_on_hands; i++) {
                team.addCard(getNextCard());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Состав игры:\n");
        for (Team team : teams) {
            sb.append("Команда ").append(team.getName()).append(":\n");
            for (UserChat userChat : team.userChats) {
                sb.append("Игрок ").append(userChat.getName()).append("\n");
            }
        }
        sendTextToAll(sb.toString());
        nextRound();


    }

    private Team getActiveTeam() {
        return teams.get(indexActiveTeam);
    }

    private UserChat getActivePlayer() {
        return getActiveTeam().getActivePlayer();
    }

    private void requestVote(UserChat user) {
        user.indexCode++;
        if (user.indexCode < code.length) {
            String s = "";
            if (user.getTeam() == getActiveTeam()) {
                s += ", вот ваши карточки:\n";
                s += user.getTeam().getCardsToString();
            }
            bot.sendKeyBoard(user.getId(), "Выберите цифру соответствующую данной ассоциации" + s, user.getTeam().getVotes());
            String card = getActiveTeam().getCards().get(code[user.indexCode]);
            bot.forwardMessage(user.getId(), getActiveTeam().getCurrentAssociates().get(card));
        } else {
            countVotePlayers++;
            if (countVotePlayers >= teams.size())
                showResult();

        }
    }

    private void showResult() {

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
            if (guessed && team != getActiveTeam() && !getActiveTeam().isFirstTurn())
                team.winCards++;
            else if (!guessed && team == getActiveTeam())
                team.loseCards++;
            sb.append(team.getName()).append(" = ").append(team.getResultVotesToString());
            sb.append("(+").append(team.winCards).append(", -").append(team.loseCards).append(")\n");
        }

        sendTextToAll(sb.toString());
        getActiveTeam().setFirstTurn(false);
        boolean end = false;
        for (Team team : teams) {
            if (team.winCards == WIN_CARDS) {
                end = true;
                sendTextToAll(team.getName() + " выиграли");
            }
            if (team.loseCards == WIN_CARDS) {
                end = true;
                sendTextToAll(team.getName() + " проиграли");
            }
        }
        if (end)
            finishGame();
        else {
            getActiveTeam().incIndexActivePlayer();
            for (String key : getActiveTeam().getAssociates().keySet()) {
                Message msg = getActiveTeam().getCurrentAssociates().get(key);
                if (msg != null)
                    getActiveTeam().getAssociates().get(key).add(msg);
            }
            indexActiveTeam++;
            if (indexActiveTeam >= teams.size())
                indexActiveTeam = 0;
            nextRound();
        }
    }

    private void runVote(UserChat user) {
        for (UserChat userChat : user.getTeam().userChats) {
            userChat.setStatus(UserChat.OK);
        }
        user.setStatus(UserChat.VOTE);
        user.indexCode = -1;
        requestVote(user);
    }

    Stack<String> loadCards() {
        Stack<String> result = new Stack<>();

        java.util.function.Function<String, List<String>> readFromClasspath = (charsetName) -> {
            List<String> lines = new ArrayList<>();
            try (InputStream is = Game.class.getClassLoader().getResourceAsStream(WORDS_FILE_NAME)) {
                if (is == null) return lines;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName(charsetName)))) {
                    String line = reader.readLine();
                    while (line != null) {
                        line = line.trim();
                        if (!line.isEmpty()) lines.add(line);
                        line = reader.readLine();
                    }
                }
            } catch (IOException ignored) {
            }
            return lines;
        };

        java.util.function.Function<String, List<String>> readFromFile = (charsetName) -> {
            List<String> lines = new ArrayList<>();
            try {
                File file = new File(WORDS_FILE_NAME);
                if (file.exists()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), Charset.forName(charsetName)))) {
                        String line = reader.readLine();
                        while (line != null) {
                            line = line.trim();
                            if (!line.isEmpty()) lines.add(line);
                            line = reader.readLine();
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            return lines;
        };

        // 1) Try classpath UTF-8
        List<String> lines = readFromClasspath.apply("UTF8");
        // 2) Fallback to filesystem UTF-8
        if (lines.isEmpty()) lines = readFromFile.apply("UTF8");
        // 3) If looks garbled (U+FFFD), try Windows-1251 from classpath, then filesystem
        boolean hasReplacement = false;
        for (String s : lines) {
            if (s.indexOf('\uFFFD') >= 0) {
                hasReplacement = true;
                break;
            }
        }
        if (hasReplacement) {
            List<String> alt = readFromClasspath.apply("windows-1251");
            if (alt.isEmpty()) alt = readFromFile.apply("windows-1251");
            if (!alt.isEmpty()) lines = alt;
        }

        for (String s : lines) result.push(s);
        return result;
    }

    Stack<String> getCards() {
        Stack<String> result = new Stack<>();
        try {
            File file = new File(WORDS_FILE_NAME);
            //создаем объект FileReader для объекта File

            //создаем BufferedReader с существующего FileReader для построчного считывания
            BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8));
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
        // Fallback: if text looks garbled (contains Unicode replacement char),
        // try reading the words file using Windows-1251 encoding.
        boolean _hasReplacement = false;
        for (String _s : result) {
            if (_s.indexOf('\uFFFD') >= 0) {
                _hasReplacement = true;
                break;
            }
        }
        if (_hasReplacement) {
            try {
                result.clear();
                File file = new File(WORDS_FILE_NAME);

                BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), Charset.forName("windows-1251")));
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
