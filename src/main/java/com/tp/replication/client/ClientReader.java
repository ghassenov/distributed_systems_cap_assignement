package com.tp.replication.client;

import com.tp.replication.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Profile("reader")
public class ClientReader implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(ClientReader.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Value("${client.read-timeout-ms:5000}")
    private long readTimeoutMs;

    @Override
    public void run(String... args) throws Exception {
        logger.info("ClientReader started - sending READ_LAST request");
        
        String correlationId = UUID.randomUUID().toString();
        String replyQueueName = "reply_" + correlationId;

        // Create temporary exclusive queue for reply
        Queue replyQueue = new Queue(replyQueueName, false, false, true);
        rabbitAdmin.declareQueue(replyQueue);
        
        logger.info("Created temporary reply queue: {}", replyQueueName);

        // Setup listener for reply
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(replyQueueName);
        container.setMessageListener(message -> {
            String reply = new String(message.getBody());
            logger.info("Received reply: {}", reply);
            result.set(reply);
            latch.countDown();
        });
        container.start();

        try {
            // Send READ_LAST request
            String request = "READ_LAST:" + correlationId + ":" + replyQueueName;
            logger.info("Sending READ_LAST request: {}", request);
            rabbitTemplate.convertAndSend(RabbitMQConfig.READ_REQUEST_EXCHANGE, "", request);

            // Wait for response (5 seconds timeout)
            boolean received = latch.await(readTimeoutMs, TimeUnit.MILLISECONDS);

            if (received && result.get() != null) {
                String reply = result.get();
                String[] parts = reply.split("\\|", 2);
                if (parts.length >= 2) {
                    String lastLine = parts[1];
                    System.out.println("Last line: " + lastLine);
                    logger.info("Last line received: {}", lastLine);
                } else {
                    System.out.println("Invalid response format: " + reply);
                }
            } else {
                System.out.println("No response from any replica (timeout)");
                logger.warn("No response received within {} ms", readTimeoutMs);
            }
        } finally {
            container.stop();
            rabbitAdmin.deleteQueue(replyQueueName);
            System.exit(0);
        }
    }
}
