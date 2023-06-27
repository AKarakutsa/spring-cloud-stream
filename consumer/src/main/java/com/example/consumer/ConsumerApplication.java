package com.example.consumer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.Header;

import java.util.Map;

@SpringBootApplication
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}

@Slf4j
@AllArgsConstructor
@EnableBinding(Processor.class)
class Consumer {
    private final JdbcTemplate jdbcTemplate;

    @StreamListener(Processor.INPUT)
    public void consume(MessageDto message, @Header(name = "x-death", required = false) Map<?,?> death) {
        log.info("Obtained message [" + message + "]. Saving ...");

        if (death != null && death.get("count") != null) {
            log.info("Message [" + message.toString() + "] sending again after exception. Attempt " + (((Long)death.get("count")) + 1));
        }

        int updateCode = jdbcTemplate.update(Constants.Query.INSERT_MESSAGE, message.getName());

        log.info("Message [" + message + "] saved with code " + updateCode);
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

class Constants {
    class Query {
        public static final String INSERT_MESSAGE = "INSERT INTO MESSAGE(NAME) VALUES (?)";
    }
}
