package com.tp.replication.client;

import com.tp.replication.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

@Component
@Profile("writer")
public class ClientWriter implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(ClientWriter.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ApplicationArguments applicationArguments;

    @Override
    public void run(String... args) throws Exception {
        List<String> inputArgs = applicationArguments.getNonOptionArgs();
        logger.info("ClientWriter started with {} args", inputArgs.size());

        if (!inputArgs.isEmpty()) {
            // Command-line mode: join all args as one line
            String line = String.join(" ", inputArgs);
            sendLine(line);
        } else {
            // Interactive mode
            Scanner scanner = new Scanner(System.in);
            System.out.println("ClientWriter - Interactive Mode");
            System.out.println("Type lines to send (type 'exit' to quit):");
            
            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine();
                
                if ("exit".equalsIgnoreCase(line)) {
                    System.out.println("Exiting...");
                    break;
                }
                
                if (!line.trim().isEmpty()) {
                    sendLine(line);
                }
            }
            scanner.close();
        }

        System.exit(0);
    }

    private void sendLine(String line) {
        try {
            logger.info("Sending write message: {}", line);
            rabbitTemplate.convertAndSend(RabbitMQConfig.WRITE_EXCHANGE, "", line);
            System.out.println("Sent: " + line);
            logger.info("Write message sent successfully");
        } catch (Exception e) {
            logger.error("Failed to send write message: {}", line, e);
            System.err.println("Failed to send: " + e.getMessage());
        }
    }
}
