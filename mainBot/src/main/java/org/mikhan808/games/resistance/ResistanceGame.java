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

    private static final String START_GAME = "Начать игру";
    private static final String DONE = "Готово";
    private static final String RESET = "Сброс";
    private static final String ADD_PREFIX = "Добавить: ";
    private static final String REMOVE_PREFIX = "Удалить: ";
    private static final String MISSION_SUCCESS = "Успех";
    private static final String MISSION_FAIL = "Провал";

    private final List<ResistanceUserChat> players = new ArrayList<>();
    private final List<ResistanceUserChat> proposedTeam = new ArrayList<>();
    private final Map<Long, Boolean> teamVotes = new HashMap<>();
    private final Map<Long, Boolean> missionVotes = new HashMap<>();
    private boolean started = false;
    private int leaderIndex = 0;
    private int missionNumber = 1; // 1..5
    private int rejectsInRow = 0;
    private int successes = 0;
    private int failures = 0;

    public ResistanceGame(Bot bot) {
        super(bot);
    }

    @Override
    public void readMsg(Update update) {
        Message msg = update.getMessage();
        Long id = msg.getChatId();
        ResistanceUserChat user = findUser(id);
        if (user == null) {
            UserChat lobby = bot.findUser(id);
            if (lobby != null) lobby.setGame(null);
            bot.sendText(id, "Вы не в игре. Присоединитесь через меню.");
            return;
        }

        if (!started) {
            if (START_GAME.equals(msg.getText()) && isLeader(user) && players.size() >= 5) {
                startGame();
            } else {
                sendLobbyStatus();
                sendStartButtonToLeaderIfReady();
            }
            return;
        }

        switch (user.getStatus()) {
            case ResistanceUserChat.OK:
                // ignore
                break;
            case ResistanceUserChat.TEAM_SELECTION:
                if (isLeader(user)) handleTeamSelection(msg.getText(), user);
                else bot.sendText(id, "Команду выбирает лидер.");
                break;
            case ResistanceUserChat.TEAM_VOTE:
                handleTeamVote(msg.getText(), user);
                break;
            case ResistanceUserChat.MISSION_VOTE:
                handleMissionVote(msg.getText(), user);
                break;
        }
    }

    @Override
    public boolean addPlayer(UserChat lobbyUser) {
        ResistanceUserChat u = new ResistanceUserChat(lobbyUser.getId(), lobbyUser.getName());
        players.add(u);
        lobbyUser.setGame(this);
        u.setStatus(ResistanceUserChat.OK);
        sendLobbyStatus();
        sendStartButtonToLeaderIfReady();
        return true;
    }

    private ResistanceUserChat findUser(Long id) {
        for (ResistanceUserChat u : players) if (u.getId().equals(id)) return u;
        return null;
    }

    private ResistanceUserChat findUserByName(String name) {
        for (ResistanceUserChat u : players) if (name.trim().equalsIgnoreCase(u.getName())) return u;
        return null;
    }

    private boolean isLeader(ResistanceUserChat u) {
        return players.indexOf(u) == leaderIndex;
    }

    private void sendLobbyStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Игроки (" + players.size() + "): ");
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(players.get(i).getName() == null ? "(без имени)" : players.get(i).getName());
        }
        if (!players.isEmpty()) sb.append("\nЛидер: ").append(players.get(leaderIndex).getName());
        sendTextToAll(sb.toString());
    }

    private void sendStartButtonToLeaderIfReady() {
        if (players.size() >= 5 && players.size() <= 10) {
            ResistanceUserChat leader = players.get(leaderIndex);
            bot.sendKeyBoard(leader.getId(), "Лидер, начните игру, когда все готовы.", Collections.singletonList(START_GAME));
        }
    }

    private void startGame() {
        started = true;
        assignSpies();
        missionNumber = 1;
        successes = 0;
        failures = 0;
        rejectsInRow = 0;
        startMission();
    }

    private void assignSpies() {
        int n = players.size();
        int spies = n <= 6 ? 2 : (n <= 9 ? 3 : 4);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        Collections.shuffle(idx, new Random());
        for (int i = 0; i < spies; i++) players.get(idx.get(i)).setSpy(true);
        for (ResistanceUserChat p : players) {
            bot.sendText(p.getId(), p.isSpy() ? "Вы шпион" : "Вы член сопротивления");
        }
    }

    private void startMission() {
        proposedTeam.clear();
        teamVotes.clear();
        missionVotes.clear();
        int required = requiredTeamSize();
        String header = "Миссия " + missionNumber + ". Требуется игроков: " + required + "\nЛидер: " + players.get(leaderIndex).getName();
        sendTextToAll(header);
        ResistanceUserChat leader = players.get(leaderIndex);
        leader.setStatus(ResistanceUserChat.TEAM_SELECTION);
        presentTeamSelection(leader);
    }

    private void presentTeamSelection(ResistanceUserChat leader) {
        int required = requiredTeamSize();
        List<String> buttons = new ArrayList<>();
        for (ResistanceUserChat p : players) {
            if (proposedTeam.contains(p)) buttons.add(REMOVE_PREFIX + p.getName());
            else buttons.add(ADD_PREFIX + p.getName());
        }
        buttons.add(DONE);
        buttons.add(RESET);
        bot.sendKeyBoard(leader.getId(), "Выберите " + required + " участника (сейчас: " + proposedTeam.size() + ")", buttons);
    }

    private void handleTeamSelection(String t, ResistanceUserChat leader) {
        int required = requiredTeamSize();
        if (DONE.equals(t)) {
            if (proposedTeam.size() != required) {
                bot.sendText(leader.getId(), "Нужно выбрать ровно " + required + " игрока(ов).");
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
        if (t != null && t.startsWith(ADD_PREFIX)) {
            String name = t.substring(ADD_PREFIX.length()).trim();
            ResistanceUserChat u = findUserByName(name);
            if (u != null && !proposedTeam.contains(u) && proposedTeam.size() < required) proposedTeam.add(u);
            presentTeamSelection(leader);
            return;
        }
        if (t != null && t.startsWith(REMOVE_PREFIX)) {
            String name = t.substring(REMOVE_PREFIX.length()).trim();
            ResistanceUserChat u = findUserByName(name);
            proposedTeam.remove(u);
            presentTeamSelection(leader);
        }
    }

    private void startTeamVote() {
        teamVotes.clear();
        String teamList = formatNames(proposedTeam);
        for (ResistanceUserChat p : players) {
            p.setStatus(ResistanceUserChat.TEAM_VOTE);
            bot.sendKeyBoard(p.getId(), "Голосование за команду: " + teamList, Arrays.asList(YES, NO));
        }
    }

    private void handleTeamVote(String t, ResistanceUserChat user) {
        if (YES.equals(t)) teamVotes.put(user.getId(), true);
        else if (NO.equals(t)) teamVotes.put(user.getId(), false);
        else {
            bot.sendText(user.getId(), "Выберите Да/Нет");
            return;
        }
        user.setStatus(ResistanceUserChat.OK);
        if (teamVotes.size() == players.size()) finalizeTeamVote();
    }

    private void finalizeTeamVote() {
        int approves = 0, rejects = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("Итоги голосования команды:\n");
        for (ResistanceUserChat p : players) {
            Boolean v = teamVotes.get(p.getId());
            boolean approve = v != null && v;
            if (approve) approves++;
            else rejects++;
            sb.append(p.getName()).append(": ").append(approve ? "ДА" : "НЕТ").append("\n");
        }
        sendTextToAll(sb.toString());
        if (approves > rejects) {
            startMissionVoting();
        } else {
            rejectsInRow++;
            if (rejectsInRow >= 5) {
                failures = 3;
                endGameIfComplete();
                return;
            }
            leaderIndex = (leaderIndex + 1) % players.size();
            startMission();
        }
    }

    private void startMissionVoting() {
        missionVotes.clear();
        sendTextToAll("Команда отправилась на миссию. Голосуют только участники команды.");
        for (ResistanceUserChat p : players) {
            if (proposedTeam.contains(p)) {
                p.setStatus(ResistanceUserChat.MISSION_VOTE);
                if (p.isSpy()) bot.sendKeyBoard(p.getId(), "Ваш выбор:", Arrays.asList(MISSION_SUCCESS, MISSION_FAIL));
                else bot.sendKeyBoard(p.getId(), "Ваш выбор:", Collections.singletonList(MISSION_SUCCESS));
            }
        }
    }

    private void handleMissionVote(String t, ResistanceUserChat user) {
        if (MISSION_SUCCESS.equals(t)) missionVotes.put(user.getId(), true);
        else if (MISSION_FAIL.equals(t)) {
            if (!user.isSpy()) {
                bot.sendText(user.getId(), "Только шпионы могут провалить миссию");
                return;
            }
            missionVotes.put(user.getId(), false);
        } else {
            bot.sendText(user.getId(), "Выберите вариант");
            return;
        }
        user.setStatus(ResistanceUserChat.OK);
        if (missionVotes.size() == proposedTeam.size()) finalizeMission();
    }

    private void finalizeMission() {
        int fails = 0;
        for (ResistanceUserChat p : proposedTeam) {
            Boolean v = missionVotes.get(p.getId());
            if (v != null && !v) fails++;
        }
        boolean twoFailsRequired = players.size() >= 7 && missionNumber == 4;
        boolean missionFailed = twoFailsRequired ? (fails >= 2) : (fails >= 1);
        if (missionFailed) {
            failures++;
            sendTextToAll("Миссия " + missionNumber + " провалена (провалов: " + fails + ")\nСчёт: успехов=" + successes + ", провалов=" + failures);
        } else {
            successes++;
            sendTextToAll("Миссия " + missionNumber + " успешна\nСчёт: успехов=" + successes + ", провалов=" + failures);
        }
        endGameIfComplete();
        missionNumber++;
        leaderIndex = (leaderIndex + 1) % players.size();
        startMission();
    }

    private void endGameIfComplete() {
        if (successes >= 3) {
            sendTextToAll("Сопротивление победило!");
            resetAndExit();
        }
        if (failures >= 3) {
            sendTextToAll("Шпионы победили!");
            resetAndExit();
        }
    }

    private void resetAndExit() {
        for (ResistanceUserChat p : players) p.setGame(null);
        bot.removeGame(this);
    }

    private int requiredTeamSize() {
        int n = players.size();
        int[][] table = new int[][]{
                // missions 1..5 columns; rows by players 5..10
                {2, 3, 2, 3, 3}, // 5
                {2, 3, 4, 3, 4}, // 6
                {2, 3, 3, 4, 4}, // 7
                {3, 4, 4, 5, 5}, // 8
                {3, 4, 4, 5, 5}, // 9
                {3, 4, 4, 5, 5}  // 10
        };
        int row = Math.min(Math.max(n, 5), 10) - 5;
        int col = Math.min(Math.max(missionNumber, 1), 5) - 1;
        return table[row][col];
    }

    private String formatNames(List<ResistanceUserChat> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i).getName());
        }
        return sb.toString();
    }

    private void sendTextToAll(String text) {
        for (ResistanceUserChat u : players) bot.sendText(u.getId(), text);
    }
}
