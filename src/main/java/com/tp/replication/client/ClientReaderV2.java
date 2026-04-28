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

import java.util.*;

@Component
@Profile("readerv2")
public class ClientReaderV2 implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(ClientReaderV2.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Value("${client.read-all-timeout-ms:5000}")
    private long readAllTimeoutMs;

    @Value("${replica.count:3}")
    private int replicaCount;

    @Override
    public void run(String... args) throws Exception {
        logger.info("ClientReaderV2 started - sending READ_ALL request with majority voting");
        
        String correlationId = UUID.randomUUID().toString();
        String replyQueueName = "reply_" + correlationId;

        // Create temporary exclusive queue for reply
        Queue replyQueue = new Queue(replyQueueName, false, false, true);
        rabbitAdmin.declareQueue(replyQueue);
        
        logger.info("Created temporary reply queue: {}", replyQueueName);

        // Track received lines by replica
        Map<String, Set<String>> replicaLines = new HashMap<>();
        Map<String, Boolean> replicaEnded = new HashMap<>();
        Object lock = new Object();

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(replyQueueName);
        container.setMessageListener(message -> {
            String reply = new String(message.getBody());
            logger.debug("Received message: {}", reply);
            
            synchronized (lock) {
                String[] parts = reply.split("\\|", 3);
                if (parts.length >= 3) {
                    String corrId = parts[0];
                    String replicaId = parts[1];
                    String content = parts[2];

                    if (!correlationId.equals(corrId)) {
                        return;
                    }

                    if ("END".equals(replicaId)) {
                        // END marker
                        replicaEnded.put(content, true);
                        logger.info("Received END from replica: {}", content);
                    } else {
                        // Data line
                        replicaLines.computeIfAbsent(replicaId, k -> new HashSet<>()).add(content);
                        logger.debug("Stored line from {}: {}", replicaId, content);
                    }
                } else {
                    logger.warn("Invalid message format: {}", reply);
                }
            }
        });
        container.start();

        try {
            // Send READ_ALL request
            String request = "READ_ALL:" + correlationId + ":" + replyQueueName;
            logger.info("Sending READ_ALL request: {}", request);
            rabbitTemplate.convertAndSend(RabbitMQConfig.READ_REQUEST_EXCHANGE, "", request);

            // Wait for all replicas to finish (5 seconds timeout)
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < readAllTimeoutMs) {
                synchronized (lock) {
                    if (replicaEnded.size() >= replicaCount) {
                        logger.info("All replicas have responded");
                        break;
                    }
                }
                Thread.sleep(100);
            }

            // Apply majority voting
            applyMajorityVoting(replicaLines);
        } finally {
            container.stop();
            rabbitAdmin.deleteQueue(replyQueueName);
            System.exit(0);
        }
    }

    private void applyMajorityVoting(Map<String, Set<String>> replicaLines) {
        logger.info("Applying majority voting with replica data: {}", replicaLines);

        // Collect all lines from all replicas
        Map<String, Integer> lineVotes = new HashMap<>();
        for (String replicaId : replicaLines.keySet()) {
            for (String line : replicaLines.get(replicaId)) {
                lineVotes.put(line, lineVotes.getOrDefault(line, 0) + 1);
            }
        }

        // Extract line numbers and find winners
        Map<Integer, String> finalLines = new TreeMap<>();
        int requiredVotes = (replicaCount / 2) + 1;
        for (String line : lineVotes.keySet()) {
            int votes = lineVotes.get(line);
            if (votes >= requiredVotes) {
                try {
                    // Parse line number from the beginning
                    String[] parts = line.split(" ", 2);
                    if (parts.length >= 1) {
                        int lineNum = Integer.parseInt(parts[0]);
                        finalLines.put(lineNum, line);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse line number from: {}", line);
                }
            }
        }

        // Print results
        System.out.println("\n=== Reconciled File Content (Majority Voting) ===");
        if (finalLines.isEmpty()) {
            System.out.println("(empty)");
        } else {
            for (String line : finalLines.values()) {
                System.out.println(line);
            }
        }
        System.out.println("================================================\n");

        logger.info("Final reconciled content: {}", finalLines);
    }
}
