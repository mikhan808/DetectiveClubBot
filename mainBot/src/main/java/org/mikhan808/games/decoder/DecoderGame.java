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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;


public class DecoderGame extends Game {
    public static final int MIN_COUNT_TEAMS = 2;
    public static final int WIN_CARDS = 2;
    private static final String WORDS_RESOURCE_NAME = "Words.csv";

    private final List<Team> teams;

    private Stack<String> cards;
    private int count_cards_on_hands = 4;
    private int countTeams = 2;
    private int indexActiveTeam = 0;
    private int codeLength = 3;
    private int[] code;
    private boolean awaitingOwnGuess = false;
    private int pendingInterceptGuesses = 0;
    private boolean waitingForOwnReady = false;
    private int waitingInterceptReady = 0;

    public DecoderGame(Bot bot) {
        super(bot);
        teams = new ArrayList<>();
        cards = getCards();
        Collections.shuffle(cards, rand);
    }

    @Override
    protected UserChat createUserChat(UserChat lobbyUserChat) {
        return new DecoderUserChat(lobbyUserChat.getId(), lobbyUserChat.getName());
    }

    private boolean allTeamsNamed() {
        if (teams.size() < countTeams) return false;
        for (Team t : teams) if (t.getName() == null || t.getName().trim().isEmpty()) return false;
        return true;
    }

    private boolean allPlayersAssignedToTeam() {
        for (UserChat u : players) {
            boolean hasTeam = false;
            for (Team t : teams) {
                if (t.getPlayers().contains(u)) { hasTeam = true; break; }
            }
            if (!hasTeam) return false;
        }
        return true;
    }

    private boolean allPlayersJoined() { return countPlayers != 1000 && players.size() == countPlayers; }

    private boolean finishedSettings() { return allPlayersJoined() && allTeamsNamed() && allPlayersAssignedToTeam(); }

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

    private void nextRound() {
        DecoderUserChat activePlayer = getActivePlayer();
        activePlayer.setStatus(DecoderUserChat.ACTIVE_PLAYER);
        activePlayer.indexCode = -1;
        sendTextToAll("Новый раунд!");
        sendTextToAll(getActiveTeam().getName() + " — активная команда");
        sendTextToAll(activePlayer.getName() + " — активный игрок");
        sendTeam(getActiveTeam(), "Ваши карты:");
        sendTeam(getActiveTeam(), getActiveTeam().getCardsToString());
        sendTextToAll("Остальные команды ждут ассоциации");
        for (Team team : teams) team.resetRoundState();
        prepareCodeForActivePlayer();
    }

    private void handleActivePlayerCodeLength(Message msg, DecoderUserChat user) {
        user.setStatus(DecoderUserChat.ACTIVE_PLAYER_X);
        bot.sendText(user.getId(), "Длина кода фиксирована и равна " + (code != null ? code.length : codeLength) + ". Отправьте ассоциации на присланные карты.");
    }

    private void handleActivePlayerAssociate(Message msg, DecoderUserChat user) {
        getActiveTeam().getCurrentAssociates().put(user.indexCode, msg);
        if (user.indexCode < code.length - 1) {
            requestAssociate();
        } else {
            user.setStatus(DecoderUserChat.OK);
            bot.sendText(user.getId(), "Ассоциации приняты. Запускаем голосование...");
            runVote(user);
        }
    }

    private void runVote(DecoderUserChat active) {
        Team activeTeam = active.getTeam();
        for (Team team : teams) team.resetRoundState();
        awaitingOwnGuess = false;
        waitingForOwnReady = false;
        waitingInterceptReady = 0;
        pendingInterceptGuesses = 0;
        DecoderUserChat activeDecoder = getActivePlayer();
        boolean hasHistory = false;
        for (int i = 0; i < activeTeam.getCards().size(); i++) {
            List<Message> history = activeTeam.getAssociatesHistory().get(activeTeam.getCards().get(i));
            if (history != null && !history.isEmpty()) {
                hasHistory = true;
                break;
            }
        }
        if (hasHistory) {
            sendTextToAll("Ассоциации предыдущих раундов:");
            for (int i = 0; i < activeTeam.getCards().size(); i++) {
                String card = activeTeam.getCards().get(i);
                List<Message> history = activeTeam.getAssociatesHistory().get(card);
                if (history == null || history.isEmpty()) continue;
                sendTextToAll("Карта " + (i + 1) + ":");
                for (Message message : history) copyMsgToAll(message);
            }
        }

        sendTextToAll("Новые ассоциации активной команды:");
        for (int i = 0; i < code.length; i++) {
            Message current = activeTeam.getCurrentAssociates().get(i);
            if (current != null) copyMsgToAll(current);
        }

        // Собственная команда угадывает код
        List<DecoderUserChat> nonActive = new ArrayList<>();
        for (DecoderUserChat teammate : activeTeam.getPlayers()) {
            if (teammate == activeDecoder) {
                teammate.setStatus(DecoderUserChat.OK);
            } else {
                nonActive.add(teammate);
            }
        }

        if (nonActive.size() == 1) {
            DecoderUserChat guesser = nonActive.get(0);
            guesser.setStatus(DecoderUserChat.GUESS_OWN_CODE);
            bot.sendText(guesser.getId(), "Введите код вашей команды (например, 4 1 4).");
            awaitingOwnGuess = true;
        } else if (nonActive.size() > 1) {
            waitingForOwnReady = true;
            //sendTextToAll("Команда " + activeTeam.getName() + " обсуждает подсказки. Отправьте /ready, когда будете готовы назвать код.");
            sendTeam(activeTeam, "Команда " + activeTeam.getName() + ", обсудите подсказки и отправьте /ready, когда будете готовы назвать код.");
            for (DecoderUserChat teammate : nonActive) {
                teammate.setStatus(DecoderUserChat.DISCUSSION);
                bot.sendText(teammate.getId(), "Обсудите подсказки. Когда будете готовы назвать код, отправьте /ready.");
            }
            bot.sendText(activeDecoder.getId(), "Ждём сигнал /ready от команды перед вводом кода.");
        }

        boolean interceptAllowed = !activeTeam.isFirstTurn();
        if (interceptAllowed) {
            for (Team team : teams) {
                if (team == activeTeam) continue;
                List<DecoderUserChat> opponents = new ArrayList<>(team.getPlayers());
                if (opponents.isEmpty()) continue;
                if (opponents.size() == 1) {
                    DecoderUserChat interceptor = opponents.get(0);
                    interceptor.setStatus(DecoderUserChat.INTERCEPT_CODE);
                    bot.sendText(interceptor.getId(), "Попробуйте перехватить код (например, 4 1 4).");
                    pendingInterceptGuesses++;
                } else {
                    for (DecoderUserChat opponent : opponents) {
                        opponent.setStatus(DecoderUserChat.DISCUSSION);
                        bot.sendText(opponent.getId(), "Обсудите подсказки соперников. Когда будете готовы к перехвату, отправьте /ready.");
                    }
                    sendTeam(team, "Команда " + team.getName() + ", обсудите возможный перехват и отправьте /ready, когда будете готовы.");
                    waitingInterceptReady++;
                    pendingInterceptGuesses++;
                }
            }
        } else {
            for (Team team : teams) {
                if (team == activeTeam) continue;
                for (DecoderUserChat opponent : team.getPlayers()) {
                    opponent.setStatus(DecoderUserChat.OK);
                }
            }
            sendTextToAll("Перехват в первом раунде для команды " + activeTeam.getName() + " не выполняется. Запомните подсказки.");
        }
    }

    private void showVoteResultAndContinue() {
        waitingForOwnReady = false;
        Team activeTeam = getActiveTeam();
        StringBuilder sb = new StringBuilder();
        sb.append("Код = ").append(codeToString()).append("\n");

        boolean ownCorrect = activeTeam.hasOwnGuess() && matches(activeTeam.getOwnGuess(), code);
        if (!ownCorrect) {
            activeTeam.incrementLoseCards();
            sb.append(activeTeam.getName()).append(" не угадала код.").append("\n");
        } else {
            sb.append(activeTeam.getName()).append(" верно угадала свой код: ").append(formatGuess(activeTeam.getOwnGuess())).append("\n");
        }

        if (!activeTeam.isFirstTurn()) {
            for (Team team : teams) {
                if (team == activeTeam) continue;
                if (team.hasInterceptGuess() && matches(team.getInterceptGuess(), code)) {
                    team.incrementWinCards();
                    sb.append("Перехват от команды ").append(team.getName()).append(": ").append(formatGuess(team.getInterceptGuess())).append(" (успех)").append("\n");
                } else if (team.hasInterceptGuess()) {
                    sb.append("Перехват от команды ").append(team.getName()).append(": ").append(formatGuess(team.getInterceptGuess())).append(" (мимо)").append("\n");
                } else {
                    sb.append("Команда ").append(team.getName()).append(" не отправила перехват.").append("\n");
                }
            }
        }

        sendTextToAll(sb.toString());
        activeTeam.setFirstTurn(false);
        boolean end = false;
        for (Team team : teams) {
            if (team.getWinCards() == WIN_CARDS || team.getLoseCards() == WIN_CARDS) {
                end = true;
                break;
            }
        }
        if (end) finishGame(); else {
            getActiveTeam().incIndexActivePlayer();
            for (Map.Entry<Integer, Message> entry : getActiveTeam().getCurrentAssociates().entrySet()) {
                Message m = entry.getValue();
                if (m == null) continue;
                int pos = entry.getKey();
                if (pos < 0 || pos >= code.length) continue;
                String card = getActiveTeam().getCards().get(code[pos]);
                getActiveTeam().getAssociatesHistory().computeIfAbsent(card, k -> new ArrayList<>()).add(m);
            }
            indexActiveTeam = (indexActiveTeam + 1) % teams.size();
            nextRound();
        }
    }

    private void handleOwnGuess(Message msg, DecoderUserChat user) {
        if (!msg.hasText()) return;
        Team team = user.getTeam();
        if (team != getActiveTeam()) return;
        if (team.hasOwnGuess()) {
            user.setStatus(DecoderUserChat.OK);
            bot.sendText(user.getId(), "Код уже отправлен: " + formatGuess(team.getOwnGuess()));
            return;
        }
        try {
            int[] guess = parseGuess(msg.getText(), code.length, team.getCards().size());
            team.setOwnGuess(guess);
            awaitingOwnGuess = false;
            user.setStatus(DecoderUserChat.OK);
            for (DecoderUserChat teammate : team.getPlayers()) {
                if (teammate != getActivePlayer()) teammate.setStatus(DecoderUserChat.OK);
            }
            bot.sendText(user.getId(), "Код записан: " + formatGuess(guess));
            checkRoundResolution();
        } catch (IllegalArgumentException e) {
            bot.sendText(user.getId(), e.getMessage());
        }
    }

    private void handleInterceptGuess(Message msg, DecoderUserChat user) {
        if (!msg.hasText()) return;
        Team team = user.getTeam();
        if (team == getActiveTeam()) return;
        if (team.hasInterceptGuess()) {
            user.setStatus(DecoderUserChat.OK);
            bot.sendText(user.getId(), "Перехват уже отправлен: " + formatGuess(team.getInterceptGuess()));
            return;
        }
        try {
            int[] guess = parseGuess(msg.getText(), code.length, getActiveTeam().getCards().size());
            team.setInterceptGuess(guess);
            pendingInterceptGuesses = Math.max(0, pendingInterceptGuesses - 1);
            for (DecoderUserChat teammate : team.getPlayers()) teammate.setStatus(DecoderUserChat.OK);
            bot.sendText(user.getId(), "Перехват записан: " + formatGuess(guess));
            checkRoundResolution();
        } catch (IllegalArgumentException e) {
            bot.sendText(user.getId(), e.getMessage());
        }
    }

    private void handleDiscussionMessage(Message msg, DecoderUserChat user) {
        if (msg.hasText()&&("/ready".equals(msg.getText()) || "ready".equals(msg.getText())))
        {
            Team team = user.getTeam();
            DecoderUserChat activeDecoder = getActivePlayer();
            if (team == getActiveTeam()) {
                if (!waitingForOwnReady) {
                    bot.sendText(user.getId(), "Код уже вводится.");
                    return;
                }
                waitingForOwnReady = false;
                awaitingOwnGuess = false;
                sendTeam(team, "Команда " + team.getName() + ", вводим код. Отправьте цифры по порядку (например, 4 1 4).");
                for (DecoderUserChat teammate : team.getPlayers()) {
                    if (teammate == activeDecoder) continue;
                    teammate.setStatus(DecoderUserChat.GUESS_OWN_CODE);
                    bot.sendText(teammate.getId(), "Введите код вашей команды (например, 4 1 4).");
                    awaitingOwnGuess = true;
                }
            } else {
                if (waitingInterceptReady == 0) {
                    bot.sendText(user.getId(), "Перехват уже активирован.");
                    return;
                }
                waitingInterceptReady = Math.max(0, waitingInterceptReady - 1);
                for (DecoderUserChat opponent : team.getPlayers()) {
                    opponent.setStatus(DecoderUserChat.INTERCEPT_CODE);
                    bot.sendText(opponent.getId(), "Введите перехват (например, 4 1 4).");
                }
            }
        } else {
            forwardMsgToTeam(msg,user.getTeam());
        }
    }

    private void checkRoundResolution() {
        if (!awaitingOwnGuess && pendingInterceptGuesses == 0 && !waitingForOwnReady && waitingInterceptReady == 0) {
            showVoteResultAndContinue();
        }
    }

    private int[] parseGuess(String text, int expectedLength, int maxValue) {
        String cleaned = text.replaceAll("[^0-9]", "");
        if (cleaned.length() != expectedLength)
            throw new IllegalArgumentException("Нужно указать " + expectedLength + " цифры. Пример: 4 1 4");
        int[] result = new int[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            int digit = Character.digit(cleaned.charAt(i), 10);
            if (digit < 1 || digit > maxValue)
                throw new IllegalArgumentException("Каждая цифра должна быть от 1 до " + maxValue + ".");
            result[i] = digit - 1;
        }
        return result;
    }

    private String formatGuess(int[] guess) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < guess.length; i++) {
            if (i != 0) builder.append(' ');
            builder.append(guess[i] + 1);
        }
        return builder.toString();
    }

    private boolean matches(int[] guess, int[] actual) {
        if (guess == null || actual == null || guess.length != actual.length) return false;
        for (int i = 0; i < guess.length; i++) {
            if (guess[i] != actual[i]) return false;
        }
        return true;
    }

    public void finishGame() {
        sendTextToAll("Игра завершена");
        for (UserChat u : players) u.setGame(null);
        bot.removeGame(this);
    }

    private void forwardMsgToTeam(Message message,Team team)
    {
        forwardMsgToList(message,team.getPlayers());
    }

    private Team getActiveTeam() { return teams.get(indexActiveTeam); }
    private DecoderUserChat getActivePlayer() { return getActiveTeam().getActivePlayer(); }
    private void sendTeam(Team team, String text) { for (DecoderUserChat u : team.getPlayers()) bot.sendText(u.getId(), text); }

    private void requestAssociate() {
        DecoderUserChat ap = getActivePlayer();
        ap.indexCode++;
        bot.sendText(ap.getId(), "Введите ассоциацию к карте '" + getActiveTeam().getCards().get(code[ap.indexCode]) + "'");
    }

    private void prepareCodeForActivePlayer() {
        DecoderUserChat activePlayer = getActivePlayer();
        activePlayer.indexCode = -1;
        int cardsCount = activePlayer.getTeam().getCards().size();
        int maxAllowed = cardsCount - 1;
        int effectiveLength = Math.max(2, Math.min(codeLength, maxAllowed));
        code = new int[effectiveLength];
        for (int i = 0; i < effectiveLength; i++) {
            code[i] = rand.nextInt(cardsCount);
        }
        getActiveTeam().getCurrentAssociates().clear();
        bot.sendText(activePlayer.getId(), "Длина кода = " + effectiveLength);
        bot.sendText(activePlayer.getId(), codeToString());
        bot.sendText(activePlayer.getId(), "Отправьте ассоциации на каждую карту по порядку");
        activePlayer.setStatus(DecoderUserChat.ACTIVE_PLAYER_X);
        requestAssociate();
    }
    private String codeToString() { StringBuilder x = new StringBuilder(); for (int i = 0; i < code.length; i++) { if (i != 0) x.append(", "); x.append(code[i] + 1);} return x.toString(); }
    private String getNextCard() { if (cards.empty()) { cards = getCards(); Collections.shuffle(cards, rand);} return cards.pop(); }

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

    private Team findTeam(String name) {
        for (Team team : teams) if (team.getName() != null && name.trim().equalsIgnoreCase(team.getName())) return team;
        return null;
    }

    @Override
    public void processMsg(Update update) {
        Message msg = update.getMessage();
        Long id = msg.getChatId();
        DecoderUserChat user = (DecoderUserChat) findUser(id);
        if (user != null) {
            switch (user.getStatus()) {
                case DecoderUserChat.ENTER_NAME:
                    enterName(msg, user);
                    break;
                case DecoderUserChat.ENTER_COUNT_PLAYERS:
                    enterCountPlayers(msg, user);
                    break;
                case DecoderUserChat.ENTER_COUNT_TEAMS:
                    enterCountTeams(msg, user);
                    break;
                case DecoderUserChat.ENTER_COUNT_CARDS:
                    enterCountCards(msg, user);
                    break;
                case DecoderUserChat.ENTER_CODE_LENGTH:
                    enterCodeLength(msg, user);
                    break;
                case DecoderUserChat.ENTER_NAME_TEAM:
                    enterTeamName(msg, user);
                    break;
                case DecoderUserChat.OK:
                    bot.sendText(id, "Ожидайте, игра настраивается");
                    break;
                case DecoderUserChat.ACTIVE_PLAYER:
                    handleActivePlayerCodeLength(msg, user);
                    break;
                case DecoderUserChat.ACTIVE_PLAYER_X:
                    handleActivePlayerAssociate(msg, user);
                    break;
                case DecoderUserChat.DISCUSSION:
                    handleDiscussionMessage(msg, user);
                    break;
                case DecoderUserChat.GUESS_OWN_CODE:
                    handleOwnGuess(msg, user);
                    break;
                case DecoderUserChat.INTERCEPT_CODE:
                    handleInterceptGuess(msg, user);
                    break;
            }
        } else {
            bot.sendText(id, "Вы не в игре. Создайте/войдите через меню.");
            UserChat lobby = bot.findUser(id);
            if (lobby != null) lobby.setGame(null);
        }
    }


    private void enterName(Message msg, DecoderUserChat user) {
        Long id = user.getId();
        String name = msg.getText();
        DecoderUserChat userForName =(DecoderUserChat) findUserByName(name);
        if (userForName == null) {
            user.setName(name);
            if (players.indexOf(user) == 0) {
                bot.sendText(id, "Сколько игроков будет в игре?");
                user.setStatus(DecoderUserChat.ENTER_COUNT_PLAYERS);
            } else {
                if (teams.size() < countTeams && players.indexOf(user) >= teams.size()) {
                    createTeam(user);
                    bot.sendText(id, "Введите название вашей команды");
                    user.setStatus(DecoderUserChat.ENTER_NAME_TEAM);
                } else {
                    user.setStatus(DecoderUserChat.OK);
                    bot.sendText(id, "Ожидайте, игра настраивается. Остальные команды ещё формируются.");
                    // If everyone is ready, continue configuring the game automatically.
                    if (allPlayersJoined() && allTeamsNamed()) {
                        checkFinishSettingsForBeginGame(user);
                    }
                }
            }
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
            bot.sendText(id, "Введите число");
            user.setStatus(DecoderUserChat.ENTER_COUNT_PLAYERS);
        }
    }

    private void enterCountCards(Message msg, DecoderUserChat user) {
        Long id = user.getId();
        try {
            count_cards_on_hands = Integer.parseInt(msg.getText().trim());
            if (count_cards_on_hands < 3) {
                bot.sendText(id, "Нужно как минимум 3 карты на руках, чтобы код имел длину не менее 2.");
                user.setStatus(DecoderUserChat.ENTER_COUNT_CARDS);
                return;
            }
            bot.sendText(id, "Ок. Карт на руках: " + count_cards_on_hands);
            int maxLength = count_cards_on_hands - 1;
            bot.sendText(id, "Какую длину кода использовать в каждой партии? (2.." + maxLength + ")");
            user.setStatus(DecoderUserChat.ENTER_CODE_LENGTH);
        } catch (Exception e) {
            bot.sendText(id, "Введите число");
            user.setStatus(DecoderUserChat.ENTER_COUNT_CARDS);
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
            bot.sendText(id, "Введите число");
            user.setStatus(DecoderUserChat.ENTER_COUNT_TEAMS);
        }
    }

    private void enterCodeLength(Message msg, DecoderUserChat user) {
        Long id = user.getId();
        try {
            int requested = Integer.parseInt(msg.getText().trim());
            int maxAllowed = count_cards_on_hands - 1;
            if (requested < 2 || requested > maxAllowed) {
                bot.sendText(id, "Длина кода должна быть от 2 до " + maxAllowed + ".");
                user.setStatus(DecoderUserChat.ENTER_CODE_LENGTH);
                return;
            }
            codeLength = requested;
            bot.sendText(id, "Код будет длиной " + codeLength + ".");
            bot.sendText(id, "Сколько команд? (минимум " + MIN_COUNT_TEAMS + ")");
            user.setStatus(DecoderUserChat.ENTER_COUNT_TEAMS);
        } catch (Exception e) {
            bot.sendText(id, "Введите число");
            user.setStatus(DecoderUserChat.ENTER_CODE_LENGTH);
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

    private void createTeam(DecoderUserChat user) { Team team = new Team(); teams.add(team); team.addPlayer(user); }

    private void checkFinishSettingsForBeginGame(DecoderUserChat user) {
        // First, distribute any players that still do not have a team.
        for (int i = 0; i < players.size(); i++) {
            DecoderUserChat u = (DecoderUserChat) players.get(i);
            boolean hasTeam = false;
            for (Team tt : teams) {
                if (tt.getPlayers().contains(u)) { hasTeam = true; break; }
            }
            if (!hasTeam && !teams.isEmpty()) {
                Team target = findTeamWithFewestPlayers();
                if (target != null) target.addPlayer(u);
            }
        }
        if (finishedSettings()) {
            beginGame();
        } else {
            user.setStatus(DecoderUserChat.OK);
            bot.sendText(user.getId(), "Вы добавлены в команду. Ожидайте начала");
        }
    }

    private Team findTeamWithFewestPlayers() {
        Team target = null;
        for (Team team : teams) {
            if (target == null || team.getPlayers().size() < target.getPlayers().size()) {
                target = team;
            }
        }
        return target;
    }

    @Override
    public int getMinCountPlayers() {
        return 4;
    }
}
