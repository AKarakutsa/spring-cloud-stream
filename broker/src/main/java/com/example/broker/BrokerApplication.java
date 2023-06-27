package com.example.broker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.SendTo;

import java.util.Map;

@Slf4j
@SpringBootApplication
public class BrokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrokerApplication.class, args);
    }
}


@EnableBinding(Processor.class)
@Slf4j
@AllArgsConstructor
class Broker {
    private static final String ORIGINAL_QUEUE = "so8400in.so8400";
    private static final String DLQ = ORIGINAL_QUEUE + ".dlq";
    private static final String X_RETRIES_HEADER = "x-retries";

    private RabbitTemplate rabbitTemplate;

    @StreamListener(Processor.INPUT)
    @SendTo(Processor.OUTPUT)
    public MessageDto listen(MessageDto message, @Header(name = "x-death", required = false) Map<?,?> death) {
        if (death != null && death.get("count") != null) {
            log.info("Message [" + message.toString() + "] sending again after exception. Attempt " + (((Long)death.get("count")) + 1));
        }

        return message;
    }

    @RabbitListener(queues = DLQ)
    public void rePublish(Message failedMessage) {

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

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
class MessageDto {
    private String name;
    private boolean sync;
}

