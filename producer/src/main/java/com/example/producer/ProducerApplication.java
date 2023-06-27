package com.example.producer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class ProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }

    private static final String ORIGINAL_QUEUE = "so8400source.so8400";
    private static final String DLQ = ORIGINAL_QUEUE + ".dlq";
    private static final String X_RETRIES_HEADER = "x-retries";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @StreamListener(Processor.INPUT)
    @SendTo(Processor.OUTPUT)
    public MessageDto listen(MessageDto message, @Header(name = "x-death", required = false) Map<?,?> death) {
        if (death != null && death.get("count") != null) {
            log.info("MessageDto [" + message.toString() + "] sending again after exception. Attempt " + (((Long)death.get("count")) + 1));
        }

        return message;
    }

    @RabbitListener(queues = DLQ)
    public void rePublish(org.springframework.amqp.core.Message failedMessage) {

        log.info("DLQ obtained a message. Try republish...");

        //write message to disk
        failedMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        //add retry count
        Integer retriesHeader = (Integer) failedMessage.getMessageProperties().getHeaders().get(X_RETRIES_HEADER);
        if (retriesHeader == null) {
            retriesHeader = 1;
        }
        failedMessage.getMessageProperties().getHeaders().put(X_RETRIES_HEADER, retriesHeader + 1);

        rabbitTemplate.send(ORIGINAL_QUEUE, failedMessage);

        log.info(failedMessage.toString() + " was send to " + ORIGINAL_QUEUE);
    }

    @StreamListener("errorChannel")
    public void error(org.springframework.messaging.Message<?> message) {
        System.out.println("Handling ERROR: " + message);
    }
}

@Slf4j
@Component
@AllArgsConstructor
class DataGenerator {
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedRate = 10000)
    void generate() {
        jdbcTemplate.update(Constants.Query.INSERT_MESSAGE, UUID.randomUUID().toString());
    }
}

@Slf4j
@EnableBinding({Processor.class})
@AllArgsConstructor
class DataSender {
    private final JdbcTemplate jdbcTemplate;
    private final Processor processor;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedRate = 1000)
    void send() {
        final String exchange = "so8400in";
        boolean exist = rabbitTemplate.execute(channel -> {
            try {
                log.debug("channel.exchangeDeclarePassive(exchange)");
                return channel.exchangeDeclarePassive(exchange);
            }
            catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Exchange '" + exchange + "' does not exist");
                }
                return null;
            }
        }) != null;
        MessageDto message = jdbcTemplate.queryForObject(Constants.Query.SELECT_UNSYNCED_MESSAGE, (rs, rowNum) -> new MessageDto(
                rs.getString("NAME"),
                rs.getBoolean("SYNC")));

        if (message == null) {
            log.info("All messages are synced");
        } else {
            boolean sended = processor.input().send(MessageBuilder.withPayload(message).build());

            if (sended) {
                log.info(message + " was successfully send");
                int updateCode = jdbcTemplate.update(Constants.Query.UPDATE_SYNC_MESSAGE, new Date(), message.getName());
                log.info("MessageDto sync [" + message + "] updated with code " + updateCode);
            } else {
                log.error(message + " was not send");
            }
        }
    }
}

@Data
@AllArgsConstructor
@ToString
class MessageDto {
    private final String name;
    private final boolean sync;
}

class Constants {
    class Query {
        public static final String INSERT_MESSAGE = "INSERT INTO MESSAGE(NAME) VALUES (?)";
        public static final String SELECT_UNSYNCED_MESSAGE = "SELECT * FROM MESSAGE WHERE SYNC IS NULL ORDER BY SYNC LIMIT 1";
        public static final String UPDATE_SYNC_MESSAGE = "UPDATE MESSAGE SET SYNC = 1, UPDATE_DATE = ? WHERE NAME=?";
    }
}