package org.mikhan808;

import org.apache.logging.log4j.LogManager;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

public class Main {
    public static void main(String ... args)
    {
        try {
            TelegramBotsApi telegramBotsApi = createTelegramBotsApi();
            try {
                telegramBotsApi.registerBot(new Bot());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
           e.printStackTrace();
        }
    }
    private static TelegramBotsApi createTelegramBotsApi() {
        return createLongPollingTelegramBotsApi();
    }

    private static TelegramBotsApi createLongPollingTelegramBotsApi() {
        try {
            return new TelegramBotsApi(DefaultBotSession.class);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return null;
        }
    }
}


