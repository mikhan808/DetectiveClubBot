package org.mikhan808.games.resistance;

import org.mikhan808.Bot;
import org.mikhan808.core.Game;
import org.mikhan808.core.UserChat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

import static org.mikhan808.Bot.NO;
import static org.mikhan808.Bot.YES;

public class ResistanceGame extends Game {

        // UI strings
        private static final String START_GAME = "Начать игру";
        private static final String DONE = "Готово";
        private static final String RESET = "Сброс";
        private static final String ADD_PREFIX = "Добавить: ";
        private static final String REMOVE_PREFIX = "Убрать: ";
        private static final String MISSION_SUCCESS = "Успех";
        private static final String MISSION_FAIL = "Провал";
        private boolean started = false;

        private int leaderIndex = 0;
        private int missionNumber = 1; // 1..5
        private int rejectsInRow = 0;
        private int successes = 0;
        private int failures = 0;

        private final List<UserChat> proposedTeam = new ArrayList<>();
        private final Map<Long, Boolean> teamVotes = new HashMap<>(); // approve=true
        private final Map<Long, Boolean> missionVotes = new HashMap<>(); // success=true

        public ResistanceGame(Bot bot) {
            super(bot);
        }


        public void processMsg(Update update) {
            Message msg = update.getMessage();
            Long id = msg.getChatId();
            ResistanceUserChat user = (ResistanceUserChat) findUser(id);
            if (user == null) {
                bot.sendText(id, "Вы не в игре. Присоединитесь через главное меню.");
                if (bot.findUser(id) != null) bot.findUser(id).setGame(null);
                return;
            }

            switch (user.getStatus()) {
                case UserChat.ENTER_NAME:
                    enterName(msg, user);
                    break;
                case UserChat.OK:
                    handleOk(msg, user);
                    break;
                case ResistanceUserChat.TEAM_SELECTION:
                    if (isLeader(user))
                        handleTeamSelection(msg, user);
                    else
                        bot.sendText(id, "Ожидайте выбора лидера.");
                    break;
                case ResistanceUserChat.TEAM_VOTE:
                    handleTeamVote(msg, user);
                    break;
                case ResistanceUserChat.MISSION_VOTE:
                    handleMissionVote(msg, user);
                    break;
                default:
                    bot.sendText(id, "Ожидайте.");
            }
        }

    @Override
    protected UserChat createUserChat(UserChat lobbyUserChat) {
        return new ResistanceUserChat(lobbyUserChat.getId(),lobbyUserChat.getName());
    }

    @Override
    public int getMinCountPlayers() {
        return 5;
    }

    private void enterName(Message msg, UserChat user) {
            if (!msg.hasText()) {
                bot.sendText(user.getId(), "Отправьте текстом желаемое имя.");
                return;
            }
            String name = msg.getText().trim();
            if (name.isEmpty()) {
                bot.sendText(user.getId(), "Имя не может быть пустым.");
                return;
            }
            if (findUserByName(name) != null) {
                bot.sendText(user.getId(), "Такое имя уже занято. Выберите другое.");
                return;
            }
            user.setName(name);
            user.setStatus(UserChat.OK);
            sendLobbyState();
            sendStartButtonToLeaderIfReady();
        }

        private void handleOk(Message msg, UserChat user) {
            if (msg.hasText() && START_GAME.equals(msg.getText())) {
                if (!isLeader(user)) {
                    bot.sendText(user.getId(), "Только лидер может запускать игру.");
                    return;
                }
                if (players.size() < 5 || players.size() > 10) {
                    bot.sendText(user.getId(), "Для старта нужно 5–10 игроков.");
                    return;
                }
                startGame();
                return;
            }
            // Ignore other messages in OK state
        }

        private void handleTeamSelection(Message msg, UserChat leader) {
            if (!msg.hasText()) return;
            String t = msg.getText();
            int required = requiredTeamSize();
            if (DONE.equals(t)) {
                if (proposedTeam.size() != required) {
                    bot.sendText(leader.getId(), "Нужно выбрать ровно " + required + " игрок(а).");
                    presentTeamSelection(leader);
                    return;
                }
                startTeamVote();
                return;
            }
            if (RESET.equals(t)) {
                proposedTeam.clear();
                presentTeamSelection(leader);
                return;
            }
            if (t.startsWith(ADD_PREFIX)) {
                String name = t.substring(ADD_PREFIX.length()).trim();
                UserChat u = findUserByName(name);
                if (u != null && !proposedTeam.contains(u) && proposedTeam.size() < required) {
                    proposedTeam.add(u);
                }
                presentTeamSelection(leader);
                return;
            }
            if (t.startsWith(REMOVE_PREFIX)) {
                String name = t.substring(REMOVE_PREFIX.length()).trim();
                UserChat u = findUserByName(name);
                proposedTeam.remove(u);
                presentTeamSelection(leader);
            }
        }

        private void handleTeamVote(Message msg, UserChat user) {
            if (!msg.hasText()) return;
            String t = msg.getText();
            if (YES.equals(t)) teamVotes.put(user.getId(), true);
            else if (NO.equals(t)) teamVotes.put(user.getId(), false);
            else {
                bot.sendText(user.getId(), "Пожалуйста, используйте кнопки Да/Нет.");
                return;
            }
            user.setStatus(UserChat.OK);
            if (teamVotes.size() == players.size()) finalizeTeamVote();
        }

        private void handleMissionVote(Message msg, ResistanceUserChat user) {
            if (!msg.hasText()) return;
            String t = msg.getText();
            if (MISSION_SUCCESS.equals(t)) missionVotes.put(user.getId(), true);
            else if (MISSION_FAIL.equals(t)) {
                // На миссии "Нет" трактуем как Провал (доступно только шпионам)
                if (!user.isSpy()) {
                    bot.sendText(user.getId(), "Рабочим доступен только 'Успех'.");
                    return;
                }
                missionVotes.put(user.getId(), false);
            } else {
                bot.sendText(user.getId(), "Используйте кнопки.");
                return;
            }
            user.setStatus(UserChat.OK);
            if (missionVotes.size() == proposedTeam.size()) finalizeMission();
        }

        private boolean isLeader(UserChat user) {
            return players.indexOf(user) == leaderIndex;
        }

        private void sendLobbyState() {
            StringBuilder sb = new StringBuilder();
            sb.append("Лобби (игроков: ").append(players.size()).append(")\n");
            for (UserChat p : players) {
                sb.append("- ").append(p.getName() == null ? "(без имени)" : p.getName()).append('\n');
            }
            if (!players.isEmpty()) {
                sb.append("Лидер: ").append(players.get(leaderIndex).getName() == null ? "(без имени)" : players.get(leaderIndex).getName());
            }
            sendTextToAll(sb.toString());
        }

        private void sendStartButtonToLeaderIfReady() {
            if (players.size() >= 5 && players.size() <= 10) {
                UserChat leader = players.get(leaderIndex);
                List<String> b = Collections.singletonList(START_GAME);
                bot.sendKeyBoard(leader.getId(), "Готовы? Лидер может начать игру.", b);
            }
        }

        private void startGame() {
            started = true;
            assignRoles();
            // Сообщения о ролях в личку
            List<String> spyNames = new ArrayList<>();
            for (UserChat p : players) if (((ResistanceUserChat)p).isSpy()) spyNames.add(p.getName());
            for (UserChat p : players) {
                if (((ResistanceUserChat)p).isSpy()) {
                    bot.sendText(p.getId(), "Ваша роль: Шпион\nШпионы: " + String.join(", ", spyNames));
                } else {
                    bot.sendText(p.getId(), "Ваша роль: Рабочий");
                }
            }
            missionNumber = 1;
            successes = 0;
            failures = 0;
            rejectsInRow = 0;
            startMission();
        }

        private void startMission() {
            proposedTeam.clear();
            teamVotes.clear();
            missionVotes.clear();
            int required = requiredTeamSize();
            String header = "Работа  " + missionNumber + ". Требуется игроков: " + required +
                    "\nЛидер: " + players.get(leaderIndex).getName();
            sendTextToAll(header);
            UserChat leader = players.get(leaderIndex);
            leader.setStatus(ResistanceUserChat.TEAM_SELECTION);
            presentTeamSelection(leader);
        }

        private void presentTeamSelection(UserChat leader) {
            int required = requiredTeamSize();
            List<String> buttons = new ArrayList<>();
            for (UserChat p : players) {
                if (proposedTeam.contains(p)) buttons.add(REMOVE_PREFIX + p.getName());
                else buttons.add(ADD_PREFIX + p.getName());
            }
            buttons.add(DONE);
            buttons.add(RESET);
            bot.sendKeyBoard(leader.getId(),
                    "Выберите " + required + " игрок(а) (выбрано: " + proposedTeam.size() + ")",
                    buttons);
        }

        private void startTeamVote() {
            teamVotes.clear();
            String teamList = formatNames(proposedTeam);
            for (UserChat p : players) {
                p.setStatus(ResistanceUserChat.TEAM_VOTE);
                bot.sendKeyBoard(p.getId(), "Голосование за команду: " + teamList + "\nОдобрить?", Arrays.asList(YES, NO));
            }
            sendTextToAll("Идёт голосование за команду.");
        }

        private void finalizeTeamVote() {
            int approves = 0;
            int rejects = 0;
            StringBuilder sb = new StringBuilder();
            sb.append("Результаты голосования:\n");
            for (UserChat p : players) {
                Boolean v = teamVotes.get(p.getId());
                boolean approve = v != null && v;
                if (approve) approves++; else rejects++;
                sb.append(p.getName()).append(": ").append(approve ? YES : NO).append('\n');
            }
            boolean approved = approves > rejects; // простое большинство
            sb.append("Итого: ").append(approves).append(" за / ").append(rejects).append(" против.\n");
            sb.append(approved ? "Команда одобрена." : "Команда отклонена.");
            sendTextToAll(sb.toString());
            for (UserChat p : players) p.setStatus(UserChat.OK);

            if (!approved) {
                rejectsInRow++;
                if (rejectsInRow >= 5) {
                    spiesWin("5 отклонений команды подряд.");
                    return;
                }
                leaderIndex = (leaderIndex + 1) % players.size();
                startMission();
            } else {
                rejectsInRow = 0;
                startMissionVoting();
            }
        }

        private void startMissionVoting() {
            missionVotes.clear();
            sendTextToAll("Команда отправилась на работу.");
            for (UserChat u : players) {
                ResistanceUserChat p = (ResistanceUserChat) u;
                if (proposedTeam.contains(p)) {
                    p.setStatus(ResistanceUserChat.MISSION_VOTE);
                    if (p.isSpy()) {
                        bot.sendKeyBoard(p.getId(), "Вы на работе. Ваш голос:", Arrays.asList(MISSION_SUCCESS, MISSION_FAIL));
                    } else {
                        bot.sendKeyBoard(p.getId(), "Вы на работе. Рабочий: только Успех.", Collections.singletonList(MISSION_SUCCESS));
                    }
                } else {
                    p.setStatus(UserChat.OK);
                }
            }
        }

        private void finalizeMission() {
            int fails = 0;
            for (UserChat p : proposedTeam) {
                Boolean v = missionVotes.get(p.getId());
                if (v != null && !v) fails++;
            }
            boolean twoFailsRequired = false;//players.size() >= 7 && missionNumber == 4;
            boolean missionFailed = twoFailsRequired ? (fails >= 2) : (fails >= 1);

            if (missionFailed) {
                failures++;
                sendTextToAll("Работа " + missionNumber + " провалена (жетоны провала: " + fails + ")\nСчёт: Успехов=" + successes + ", Провалов=" + failures);
            } else {
                successes++;
                sendTextToAll("Работа " + missionNumber + " успешна\nСчёт: Успехов=" + successes + ", Провалов=" + failures);
            }

            if (successes >= 3) {
                resistanceWin("3 успешные работы.");
                return;
            }
            if (failures >= 3) {
                spiesWin("3 провальные работы.");
                return;
            }

            missionNumber++;
            leaderIndex = (leaderIndex + 1) % players.size();
            startMission();
        }

        private void resistanceWin(String reason) {
            sendTextToAll("Победа Рабочих! " + (reason == null ? "" : reason));
            revealSpies();
            finishGame();
        }

        private void spiesWin(String reason) {
            sendTextToAll("Победа Шпионов! " + (reason == null ? "" : reason));
            revealSpies();
            finishGame();
        }

        public void finishGame() {
            sendTextToAll("Игра окончена.");
            for (UserChat p : players) p.setGame(null);
            bot.removeGame(this);
        }

        private String formatNames(List<UserChat> list) {
            List<String> names = new ArrayList<>();
            for (UserChat u : list) names.add(u.getName());
            return String.join(", ", names);
        }

        private void revealSpies() {
            List<String> spies = new ArrayList<>();
            for (UserChat p : players) {
                if (((ResistanceUserChat)p).isSpy()) spies.add(p.getName());
            }
            String msg = spies.isEmpty() ? "(шпионов не обнаружено)" : String.join(", ", spies);
            sendTextToAll("Шпионы: " + msg);
        }

        private int requiredTeamSize() {
            int n = players.size();
            // Таблица из правил (кол-во игроков команды по миссиям)
            // индексируем игроков 5..10, миссии 1..5
            int[][] table = new int[][]{
                    // 5, 6, 7, 8, 9, 10
                    {2, 2, 2, 3, 3, 3},   // миссия 1
                    {3, 3, 3, 4, 4, 4},   // миссия 2
                    {2, 4, 3, 4, 4, 4},   // миссия 3
                    {3, 3, 4, 5, 5, 5},   // миссия 4
                    {3, 4, 4, 5, 5, 5}    // миссия 5
            };
            int col;
            if (n < 5) col = 0; else if (n > 10) col = 5; else col = n - 5;
            int row = missionNumber - 1;
            if (row < 0) row = 0;
            if (row > 4) row = 4;
            return table[row][col];
        }

        private void assignRoles() {
            int n = players.size();
            int spies = spiesCount(n);
            List<Integer> idxs = new ArrayList<>();
            for (int i = 0; i < n; i++) idxs.add(i);
            Collections.shuffle(idxs, rand);
            Set<Integer> spyIdx = new HashSet<>(idxs.subList(0, spies));
            for (int i = 0; i < n; i++) {
                ((ResistanceUserChat)players.get(i)).setSpy(spyIdx.contains(i));
            }
        }

        private int spiesCount(int n) {
            // Классическая раскладка: 5->2, 6->2, 7->3, 8->3, 9->3, 10->4
            switch (n) {
                case 5:
                case 6:
                    return 2;
                case 7:
                case 8:
                case 9:
                    return 3;
                case 10:
                    return 4;
                default:
                    return Math.max(2, (n - 1) / 3); // fallback
            }
        }
}
