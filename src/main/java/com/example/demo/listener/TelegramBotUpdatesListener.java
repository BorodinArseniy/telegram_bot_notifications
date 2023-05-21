package com.example.demo.listener;

import com.example.demo.entities.NotificationTask;
import com.example.demo.service.NotificationTaskService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class TelegramBotUpdatesListener implements UpdatesListener {

    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);
    private final Pattern pattern = Pattern.compile("(\\d{1,2}\\.\\d{1,2}\\.\\d{4} \\d{1,2}:\\d{2})\\s+([А-я\\d\\s.,!?%:]+)");
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final TelegramBot telegramBot;

    private final NotificationTaskService notificationTaskService;


    public TelegramBotUpdatesListener(TelegramBot telegramBot, NotificationTaskService notificationTaskService) {
        this.telegramBot = telegramBot;
        this.notificationTaskService = notificationTaskService;
    }

    @Override
    public int process(List<Update> list) {
        try{
            list.stream().
                    filter(update -> update.message()!=null).
                    forEach(update -> {
                logger.info("Handlers update: {}", update);
                Message message = update.message();
                Long chatId = message.chat().id();
                String text = message.text();

                if ("/start".equals(text)) {
                    sendMessage(chatId, "Привет! Я помогу тебе запланировать задачу. Отправь ее в формате " +
                            "ДД.ММ.ГГГГ 00:00 выполнить_задачу");

                } else if (text!=null) {
                    Matcher matcher = pattern.matcher(text);
                    if (matcher.find()){
                        LocalDateTime dateTime = parse(matcher.group(1));
                        if (Objects.isNull(dateTime)){
                            sendMessage(chatId, "Некорректный формат даты или времени");
                        } else {
                            String txt = matcher.group(2);
                            NotificationTask notificationTask = new NotificationTask();
                            notificationTask.setChatId(chatId);
                            notificationTask.setMessage(txt);
                            notificationTask.setNotificationDateTime(dateTime);
                            notificationTaskService.save(notificationTask);
                            sendMessage(chatId, "Notification is saved");
                        }
                    }else {
                        sendMessage(chatId, "Некорректный формат сообщения");
                    }
                }
            });
        } catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return CONFIRMED_UPDATES_ALL;
    }

    private void sendMessage(Long chatId, String message){
        SendMessage sendMessage = new SendMessage(chatId, message);
        SendResponse sendResponse = telegramBot.execute(sendMessage);
        if (!sendResponse.isOk()){
            logger.error("Error during sending message: {}", sendResponse.description());
        }
    }

    @Nullable
    private LocalDateTime parse(String dateTime){
        try{
            return LocalDateTime.parse(dateTime, dateTimeFormatter);
        }catch (DateTimeParseException e){
            return null;
        }
    }

    @PostConstruct
    public void init(){
        telegramBot.setUpdatesListener(this);
    }


}
