package org.mikhan808;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.util.*;

public class Bot extends TelegramLongPollingBot {

    public static final int COUNT_CARDS_ON_HANDS = 10;

    private int countPlayers = 1000;
    private int countVotePlayers = 0;
    private int indexActivePlayer = 0;
    private int countRounds = 2;
    private final Random rand;
    private String associate;
    private int conspiratorIndex = 0;
    public static final int COUNT_CARD_ON_ROUND = 2;
    private static final String WORDS_FILE_NAME = "Words.csv";
    private final Stack<String> cards;

    public Bot() {
        super();
        userChats = new ArrayList<>();
        rand = new Random();
        cards = getCards();
        Collections.shuffle(cards, rand);
    }

    List<UserChat> userChats;

    private static final String BEGIN_DISCUSSION = "Перейти к обсуждению";

    @Override
    public String getBotUsername() {
        return BotConfig.USERNAME;
    }

    @Override
    public String getBotToken() {
        return BotConfig.TOKEN;
    }

    private UserChat findUser(Long id)
    {
        for (UserChat user:userChats)
        {
            if(user.getId().equals(id))
                return user;
        }
        return null;
    }
    private UserChat findUser(String name)
    {

        for (UserChat user:userChats)
        {
            if(name.trim().equalsIgnoreCase(user.getName()))
                return user;
        }
        return null;
    }

    @Override
    public void onUpdateReceived(Update update) {

        Message msg = update.getMessage();
        Long id = msg.getChatId();
        UserChat user = findUser(id);
        if (user!=null) {
            if(user.getStatus()==UserChat.ENTER_NAME)
            {
                String name=msg.getText();
                if(findUser(name)==null) {
                    user.setName(name);
                    if(userChats.indexOf(user)==0) {
                        user.setStatus(UserChat.ENTER_COUNT_PLAYERS);
                        sendText(id,"Введите количество игроков");
                    }
                    else {
                        user.setStatus(UserChat.OK);
                        if(userChats.size()<countPlayers)
                        sendText(id,"Ожидаем подключения всех игроков");
                        else {
                            sendTextToAll("Начинаем!");
                            UserChat activePlayer = userChats.get(indexActivePlayer);
                            activePlayer.setStatus(UserChat.ACTIVE_PLAYER);
                            sendTextToAll(activePlayer.getName() + " - активный игрок");
                            for (UserChat userChat : userChats) {
                                for (int i = 0; i < COUNT_CARDS_ON_HANDS; i++) {
                                    userChat.addCard(cards.pop());
                                }
                                sendKeyBoard(userChat.getId(), "Ожидаем ассоциацию", userChat.getCards());
                            }
                        }
                    }
                }
                else sendText(id,"Пожалуйста введите другое имя, данное имя уже занято");
            } else if(user.getStatus()==UserChat.ENTER_COUNT_PLAYERS) {
                if (msg.getText() != null)
                    try {
                        countPlayers=Integer.parseInt(msg.getText().trim());
                        user.setStatus(UserChat.OK);
                        sendText(id,"Ожидаем подключения всех игроков");
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                        sendText(id,"Что-то не так. Пожалуйста проверьте данные и повторите ввод");
                    }
            } else if (user.getStatus()==UserChat.OK)
            {
                sendText(id,"Ожидаем, потерпите)");
            }
            else if (user.getStatus()==UserChat.ACTIVE_PLAYER)
            {
                associate = msg.getText();
                conspiratorIndex =rand.nextInt(userChats.size());
                while (conspiratorIndex==indexActivePlayer)
                    conspiratorIndex =rand.nextInt(userChats.size());
                userChats.get(conspiratorIndex).setStatus(UserChat.CONSPIRATOR);
                for(UserChat player:userChats)
                {
                    if(player.getStatus()==UserChat.CONSPIRATOR)
                        sendText(player.getId(),"Вы конспиратор");
                    else sendText(player.getId(),associate);
                }
                user.setStatus(UserChat.ACTIVE_PLAYER_X);
                sendKeyBoard(user.getId(), "Отправьте первое слово", user.getCards());

            }else if (user.getStatus()==UserChat.ACTIVE_PLAYER_X) {
                String card = "";
                for (String c : user.getCards()) {
                    if (c.equals(msg.getText()))
                        card = c;
                }
                if (!card.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    user.moveCardToTable(card);
                    for (int i = userChats.indexOf(user), g = 0; g < userChats.size(); g++) {
                        if (userChats.get(i).getTable().size() > 0) {
                            sb.append(userChats.get(i).getName());
                            sb.append(" (");
                            if (userChats.get(i).getTable().size() > 1) {
                                sb.append(userChats.get(i).getTable().get(0)).append(", ").append(sb.append(userChats.get(i).getTable().get(1)));
                            } else {
                                sb.append(userChats.get(i).getTable().get(0));
                            }
                            sb.append(")\n");
                        }
                        i++;
                        if (i >= userChats.size())
                            i = 0;
                    }
                    sendTextToAll(sb.toString());
                } else {
                    sendText(id, "Воспользуйтесь кнопками бота для отправки слова");
                }
                int ind = userChats.indexOf(user);
                ind++;
                if (ind >= userChats.size())
                    ind = 0;
                user.setStatus(UserChat.OK);
                userChats.get(ind).setStatus(UserChat.ACTIVE_PLAYER_X);
                if (userChats.get(ind).getTable().size() >= COUNT_CARD_ON_ROUND) {
                    countVotePlayers = 0;
                    sendTextToAll("Ассоциация:");
                    sendTextToAll(associate);
                    for (UserChat userChat : userChats) {
                        userChat.resetVotes();
                        userChat.setGuessed(false);
                        if (userChat.getStatus() == UserChat.ACTIVE_PLAYER_X) {
                            userChat.setStatus(UserChat.OK);
                            sendText(userChat.getId(), "Объясните почему Вы положили именно эти карточки и ждите результата голосования");
                        } else {
                            userChat.setStatus(UserChat.VOTE);
                            List<String> buttons = new ArrayList<>();
                            for(int i = 0; i<userChats.size(); i++) {
                                if(i!=indexActivePlayer&& !userChats.get(i).getId().equals(userChat.getId())) {
                                    buttons.add(userChats.get(i).getName());
                                }

                            }
                            sendKeyBoard(userChat.getId(), "Расскажите почему Вы положили именно эти карточки," +
                                    "выслушайте других участников и проголосуйте за того,"
                                    + " кто по вашему мнению является конспиратором", buttons);
                        }
                    }
                } else {
                    sendTextToAll(userChats.get(ind).getName() + " кладет карточку");
                    sendKeyBoard(userChats.get(ind).getId(), "Выберите слово", userChats.get(ind).getCards());
                }

            } else if (user.getStatus() == UserChat.CONSPIRATOR) {
                sendText(id, "Ну зачем такие движения? не наводите на себя подозрения)");
            } else if (user.getStatus() == UserChat.VOTE) {
                UserChat voteUser = findUser(msg.getText());
                if (voteUser == null)
                    sendText(id, "Пожалуйста выберите игрока с помощью кнопок");
                else {
                    voteUser.incVotes();
                    user.setGuessed(userChats.indexOf(voteUser) == conspiratorIndex);
                    countVotePlayers++;
                    if (countVotePlayers < userChats.size() - 1) {
                        user.setStatus(UserChat.OK);
                        sendText(id,"Ожидайте других игроков");
                    } else {
                        user.setStatus(UserChat.OK);
                        sendText(id,"Ожидайте других игроков");
                        StringBuilder sb =new StringBuilder();
                        sb.append("Конспиратор - ").append(userChats.get(conspiratorIndex).getName()).append("\n");
                        UserChat [] usersScore = new UserChat[userChats.size()];
                        usersScore = userChats.toArray(usersScore);
                        if(userChats.get(conspiratorIndex).getVotes()>1) {
                            userChats.get(conspiratorIndex).setCurrentRoundScore(0);
                            userChats.get(indexActivePlayer).setCurrentRoundScore(0);
                        } else {
                            userChats.get(conspiratorIndex).setCurrentRoundScore(5);
                            userChats.get(indexActivePlayer).setCurrentRoundScore(4);
                        }
                        for (int i=0; i<userChats.size(); i++) {
                            if(i!=conspiratorIndex&&i!=indexActivePlayer) {
                                if(userChats.get(i).isGuessed())
                                    userChats.get(i).setCurrentRoundScore(3);
                                else
                                    userChats.get(i).setCurrentRoundScore(0);
                            }
                        }
                        for(int i=0; i<usersScore.length; i++)
                            for(int g = 0; g<usersScore.length-1; g++) {
                                if(usersScore[g+1].getScore()>usersScore[g].getScore()) {
                                    UserChat temp = usersScore[g];
                                    usersScore[g]= usersScore[g+1];
                                    usersScore[g+1] = temp;
                                }
                            }
                        for(int i=0; i<usersScore.length; i++) {
                            sb.append(i+1).append(". ").append(usersScore[i].getName()).append(" = ").append(usersScore[i].getScore());
                            sb.append(" (").append(usersScore[i].getCurrentRoundScore()).append(")\n");
                        }
                        sendTextToAll(sb.toString());
                        boolean end =false;
                        if(indexActivePlayer<userChats.size())
                            indexActivePlayer++;
                        else if (countRounds == 0) {
                            sendTextToAll("Конец игры");
                            end =true;
                        } else {
                            countRounds--;
                            indexActivePlayer = 0;
                        }
                        if(!end) {
                            for (UserChat userChat : userChats)
                                userChat.resetTable();
                            sendTextToAll("Следующий раунд");
                            UserChat activePlayer = userChats.get(indexActivePlayer);
                            activePlayer.setStatus(UserChat.ACTIVE_PLAYER);
                            sendTextToAll(activePlayer.getName() + " - активный игрок");
                            sendTextToAll("Ожидаем ассоциацию");
                        }
                    }
                }
            }

        } else {
            if (msg.getText().contentEquals(BotConfig.PASSWORD)&&userChats.size()<countPlayers) {
                user = new UserChat(id,null);
                user.setStatus(UserChat.ENTER_NAME);
                sendText(id, "Добро пожаловать");
                sendText(id, "Пожалуйста введите свое имя");
                userChats.add(user);
            } else sendText(id, "Введите пароль");
        }
    }

    private void sendTextToAll(String text)
    {
        for(UserChat user:userChats)
        {
            sendText(user.getId(),text);
        }
    }

    private void sendText(Long chatId, String text) {
        SendMessage s = new SendMessage();
        s.setChatId(chatId.toString()); // Боту может писать не один человек, и поэтому чтобы отправить сообщение, грубо говоря нужно узнать куда его отправлять
        s.setText(text);
        try { //Чтобы не крашнулась программа при вылете Exception
            execute(s);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendKeyBoard(Long chatId,String text,List<String> buttons)
    {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        for(int i=0;i<buttons.size();i+=2) {
            KeyboardRow keyboardRow = new KeyboardRow();
            keyboardRow.add(buttons.get(i));
            if(i+1<buttons.size())
            keyboardRow.add(buttons.get(i+1));
            keyboard.add(keyboardRow);
        }
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setKeyboard(keyboard);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void addChat(String id,String name)
    {
        try {
            FileWriter writer = new FileWriter(WORDS_FILE_NAME, true);
            BufferedWriter bufferWriter = new BufferedWriter(writer);
        bufferWriter.write("\n" + id+";"+name);
        bufferWriter.close();
    } catch (IOException e) {
        e.printStackTrace();
    }
    }



    private void sendHideKeyboard(Long chatId, Integer messageId,String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        /*sendMessage.enableMarkdown(true);
        sendMessage.setReplyToMessageId(messageId);*/
        sendMessage.setText(text);

       /* ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove();
        replyKeyboardRemove.setSelective(true);
        sendMessage.setReplyMarkup(replyKeyboardRemove);*/

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
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

}
