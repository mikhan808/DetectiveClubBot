package org.mikhan808.games.quiz;

import org.mikhan808.Bot;
import org.mikhan808.core.Game;
import org.mikhan808.core.UserChat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public class MultiplicationGame extends Game {
    public static final String NAME = "Таблица умножения";
    private static final int MIN_FACTOR = 1;
    private static final int MAX_FACTOR = 10;

    private int totalRounds = 10;
    private int currentRound = 0;
    private int factorA;
    private int factorB;
    private int answeredPlayers = 0;
    private int answeredOrder = 0;
    private boolean gameStarted = false;

    public MultiplicationGame(Bot bot) {
        super(bot);
    }

    @Override
    public void processMsg(Update update) {
        Message msg = update.getMessage();
        if (msg == null) return;
        MultiplicationUserChat user = (MultiplicationUserChat) findUser(msg.getChatId());
        if (user == null) return;
        if (user.getStatus() == MultiplicationUserChat.ENTER_ROUNDS) {
            handleRoundsInput(msg, user);
        } else if (user.getStatus() == MultiplicationUserChat.AWAITING_ANSWER) {
            handleAnswer(msg, user);
        } else if (msg.hasText() && msg.getText().equalsIgnoreCase("/score")) {
            sendScore(user);
        } else if (msg.hasText()) {
            bot.sendText(user.getId(), "Ответьте на вопрос или используйте /score, чтобы увидеть результат.");
        }
    }

    private void handleRoundsInput(Message msg, MultiplicationUserChat user) {
        if (!msg.hasText()) {
            bot.sendText(user.getId(), "Введите число примеров.");
            return;
        }
        try {
            int rounds = Integer.parseInt(msg.getText().trim());
            if (rounds <= 0) {
                bot.sendText(user.getId(), "Количество примеров должно быть положительным числом.");
                return;
            }
            totalRounds = rounds;
            bot.sendText(user.getId(), "Примем " + totalRounds + " примеров. Ожидаем остальных игроков.");
            user.setStatus(UserChat.OK);
            finishSetting = true;
            tryStartGame();
        } catch (NumberFormatException e) {
            bot.sendText(user.getId(), "Введите целое число, например 15.");
        }
    }

    private void handleAnswer(Message msg, MultiplicationUserChat user) {
        if (!msg.hasText()) return;
        if (user.hasAnsweredThisRound()) {
            bot.sendText(user.getId(), "Вы уже отвечали на этот пример. Ожидайте следующий.");
            return;
        }
        String text = msg.getText().trim();
        int expected = factorA * factorB;
        try {
            int answer = Integer.parseInt(text);
            user.incrementAnswered();
            user.setAnsweredThisRound(true);
            answeredPlayers++;
            if (answer == expected) {
                user.incrementCorrect();
                answeredOrder++;
                int playersCount = players.size();
                int gained = playersCount + Math.max(0, playersCount - answeredOrder);
                user.addScore(gained);
                bot.sendText(user.getId(), "Верно! " + factorA + " × " + factorB + " = " + expected + ". +" + gained + " очков.");
            } else {
                bot.sendText(user.getId(), "Неверно. Правильный ответ: " + expected + ". +0 очков.");
            }
            if (answeredPlayers == players.size()) {
                sendRoundSummary();
                startNextRound();
            }
        } catch (NumberFormatException e) {
            bot.sendText(user.getId(), "Нужно ввести число. Попробуйте снова.");
        }
    }

    private void sendScore(MultiplicationUserChat user) {
        bot.sendText(user.getId(),
                "Верных ответов: " + user.getTotalCorrect() + " из " + user.getTotalAnswered() + ", очков: " + user.getTotalScore());
    }

    private void askQuestionForAll() {
        factorA = ThreadLocalRandom.current().nextInt(MIN_FACTOR, MAX_FACTOR + 1);
        factorB = ThreadLocalRandom.current().nextInt(MIN_FACTOR, MAX_FACTOR + 1);
        answeredPlayers = 0;
        answeredOrder = 0;
        currentRound++;
        for (UserChat baseUser : players) {
            MultiplicationUserChat user = (MultiplicationUserChat) baseUser;
            user.setAnsweredThisRound(false);
            user.setStatus(MultiplicationUserChat.AWAITING_ANSWER);
            bot.sendText(user.getId(), "Раунд " + currentRound + ": сколько будет " + factorA + " × " + factorB + "?");
        }
    }

    @Override
    protected UserChat createUserChat(UserChat lobbyUserChat) {
        return new MultiplicationUserChat(lobbyUserChat.getId(), lobbyUserChat.getName());
    }

    @Override
    protected void afterEnterCountPlayers(UserChat user) {
        user.setStatus(MultiplicationUserChat.ENTER_ROUNDS);
        bot.sendText(user.getId(), "Введите количество примеров, которые нужно решить (например, 20).");
    }

    @Override
    protected void beginGame() {
        gameStarted = true;
        currentRound = 0;
        sendTextToAll("Начинаем соревнование по таблице умножения! За каждый верный ответ вы получаете " + players.size() + " очков плюс бонус за скорость. Используйте /score, чтобы увидеть личный счёт. Для выхода — /exit.");
        for (UserChat baseUser : players) {
            MultiplicationUserChat user = (MultiplicationUserChat) baseUser;
            user.resetStats();
        }
        startNextRound();
    }

    @Override
    public int getMinCountPlayers() {
        return 2;
    }

    private void tryStartGame() {
        if (gameStarted) return;
        if (!finishSetting) return;
        if (players.size() < countPlayers) return;
        boolean allNamed = true;
        for (UserChat baseUser : players) {
            if (baseUser.getName() == null) {
                allNamed = false;
                break;
            }
        }
        if (allNamed) {
            beginGame();
        }
    }

    @Override
    protected void defaultAfterEnterName(UserChat user) {
        user.setStatus(UserChat.OK);
        if (gameStarted) {
            bot.sendText(user.getId(), "Игра уже началась.");
            return;
        }
        if (finishSetting && players.size() >= countPlayers && players.stream().allMatch(u -> u.getName() != null)) {
            beginGame();
        } else {
            bot.sendText(user.getId(), "Ожидаем подключения всех игроков.");
        }
    }

    private void startNextRound() {
        if (currentRound >= totalRounds) {
            announceResults();
            finishGame();
            return;
        }
        askQuestionForAll();
    }

    private void sendRoundSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Раунд ").append(currentRound).append(" завершён.\n");
        sb.append("Ответ: ").append(factorA).append(" × ").append(factorB).append(" = ").append(factorA * factorB).append("\n");
        sb.append("Очки за раунд начислены. Текущий рейтинг:\n");
        players.stream()
                .map(u -> (MultiplicationUserChat) u)
                .sorted(Comparator.comparingInt(MultiplicationUserChat::getTotalScore).reversed())
                .forEach(u -> sb.append(u.getName())
                        .append(" — ")
                        .append(u.getTotalScore())
                        .append(" очков (верных ответов: ")
                        .append(u.getTotalCorrect())
                        .append("/" )
                        .append(u.getTotalAnswered())
                        .append(")\n"));
        sendTextToAll(sb.toString());
    }

    private void announceResults() {
        StringBuilder sb = new StringBuilder();
        sb.append("Соревнование завершено! Итоги:\n");
        players.stream()
                .map(u -> (MultiplicationUserChat) u)
                .sorted(Comparator.comparingInt(MultiplicationUserChat::getTotalScore).reversed())
                .forEach(u -> sb.append(u.getName())
                        .append(" — ")
                        .append(u.getTotalScore())
                        .append(" очков, верных ответов: ")
                        .append(u.getTotalCorrect())
                        .append(" из ")
                        .append(u.getTotalAnswered())
                        .append("\n"));
        sendTextToAll(sb.toString());
    }
}
