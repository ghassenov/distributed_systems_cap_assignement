package com.tp.replication.replica;

import com.tp.replication.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import jakarta.annotation.PostConstruct;

import java.util.List;

@Service
@Profile({"replica1", "replica2", "replica3"})
public class ReplicaService {
    private static final Logger logger = LoggerFactory.getLogger(ReplicaService.class);

    @Value("${replica.id:}")
    private String replicaId;

    @Value("${replica.data-dir:}")
    private String dataDir;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private String dataFilePath;

    @PostConstruct
    public void init() {
        if (replicaId != null && !replicaId.isEmpty()) {
            dataFilePath = dataDir + "/data.txt";
            FileUtils.ensureDirectory(dataDir);
            logger.info("Replica {} initialized with data directory: {}", replicaId, dataDir);
        }
    }

    // Write handler - queue name supplied per replica profile
    @RabbitListener(queues = "${replica.write-queue}")
    public void handleWrite(String message) {
        logger.info("[{}] Received write message: {}", replicaId, message);
        FileUtils.appendLine(dataFilePath, message);
        logger.info("[{}] Data persisted to {}", replicaId, dataFilePath);
    }

    // Listen for read requests on the replica-specific read queue
    @RabbitListener(queues = "${replica.read-queue}")
    public void handleReadRequest(String message) {
        logger.info("[{}] Received read request: {}", replicaId, message);
        
        try {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) {
                logger.warn("[{}] Invalid read request format: {}", replicaId, message);
                return;
            }

            String requestType = parts[0];
            String correlationId = parts[1];
            String replyQueueName = parts[2];

            if ("READ_LAST".equals(requestType)) {
                handleReadLast(correlationId, replyQueueName);
            } else if ("READ_ALL".equals(requestType)) {
                handleReadAll(correlationId, replyQueueName);
            } else {
                logger.warn("[{}] Unknown request type: {}", replicaId, requestType);
            }
        } catch (Exception e) {
            logger.error("[{}] Error processing read request: {}", replicaId, message, e);
        }
    }

    private void handleReadLast(String correlationId, String replyQueueName) {
        try {
            String lastLine = FileUtils.readLastLine(dataFilePath);
            String response = correlationId + "|" + lastLine;
            
            rabbitTemplate.convertAndSend(replyQueueName, response);
            logger.info("[{}] Sent READ_LAST response to {}: {}", replicaId, replyQueueName, response);
        } catch (Exception e) {
            logger.error("[{}] Error handling READ_LAST: ", replicaId, e);
        }
    }

    private void handleReadAll(String correlationId, String replyQueueName) {
        try {
            List<String> allLines = FileUtils.readAllLines(dataFilePath);
            
            // Send each line
            for (String line : allLines) {
                String response = correlationId + "|" + replicaId + "|" + line;
                rabbitTemplate.convertAndSend(replyQueueName, response);
                logger.debug("[{}] Sent line: {}", replicaId, response);
            }
            
            // Send END marker
            String endMarker = correlationId + "|END|" + replicaId;
            rabbitTemplate.convertAndSend(replyQueueName, endMarker);
            logger.info("[{}] Sent END marker for READ_ALL to {}", replicaId, replyQueueName);
        } catch (Exception e) {
            logger.error("[{}] Error handling READ_ALL: ", replicaId, e);
        }
    }
}
