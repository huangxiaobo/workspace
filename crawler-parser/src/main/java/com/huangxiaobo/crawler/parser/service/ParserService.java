package com.huangxiaobo.crawler.parser.service;

import com.google.gson.Gson;
import com.huangxiaobo.crawler.common.Constants;
import com.huangxiaobo.crawler.common.FetcherTask;
import com.huangxiaobo.crawler.common.ParseTask;
import com.huangxiaobo.crawler.common.RabbitmqClient;
import com.huangxiaobo.crawler.parser.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;

@Service
public class ParserService {

    private static final Logger logger = LoggerFactory.getLogger(ParserService.class);

    @Autowired
    public RabbitmqClient rabbitmqClient;

    @Autowired
    @Qualifier("parseTaskExecutor")
    private TaskExecutor parseTaskExecutor;

    public ParserService() {

    }


    @RabbitListener(queues = Constants.MQ_PAGE_QUEUE_NAME)
    public void receive(String message) {
        System.out.println("ParserManager [x] Received '" + message + "'");

        ParseTask parseTask;
        try {
            parseTask = new Gson().fromJson(message, ParseTask.class);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        logger.info("start do parse task: " + parseTask);

        try {
            String parserName = parseTask.getParserName();
            if (!parserName.startsWith("com.huangxiaobo.crawler.parser")) {
                parserName = "com.huangxiaobo.crawler.parser." + parserName;
            }
            Class<?> clazz = Class.forName(parserName);

            Class[] classes = new Class[]{ParseTask.class};
            Constructor constructor = clazz.getDeclaredConstructor(classes);
            constructor.setAccessible(true);

            Parser parser = (Parser) constructor.newInstance(parseTask);
            parser.setParserManager(this);

            parseTaskExecutor.execute(parser);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("start do parse task failed:" + e);
        }
    }

    public void addProcessTask(String userJson) {
        logger.info("add task to process queue");
        rabbitmqClient.sendProcessTask(userJson);
    }

    public void addFetchTask(FetcherTask task) {
        logger.info("add task to fetch queue");
        rabbitmqClient.sendFetchTask(new Gson().toJson(task));
    }
}
