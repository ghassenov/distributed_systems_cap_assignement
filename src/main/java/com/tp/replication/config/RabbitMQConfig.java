package com.tp.replication.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String WRITE_EXCHANGE = "write_exchange";
    public static final String READ_REQUEST_EXCHANGE = "read_request_exchange";
    public static final String REPLICA1_WRITE_QUEUE = "replica_write_replica1";
    public static final String REPLICA2_WRITE_QUEUE = "replica_write_replica2";
    public static final String REPLICA3_WRITE_QUEUE = "replica_write_replica3";
    public static final String REPLICA1_READ_QUEUE = "replica_read_replica1";
    public static final String REPLICA2_READ_QUEUE = "replica_read_replica2";
    public static final String REPLICA3_READ_QUEUE = "replica_read_replica3";

    // Declare fanout exchange for writes
    @Bean
    public FanoutExchange writeExchange() {
        return new FanoutExchange(WRITE_EXCHANGE, true, false);
    }

    // Declare fanout exchange for read requests so all replicas receive reads
    @Bean
    public FanoutExchange readRequestExchange() {
        return new FanoutExchange(READ_REQUEST_EXCHANGE, true, false);
    }

    // Create replica-specific queues and bind them to the fanout exchange
    @Bean
    public Queue replicaWriteQueue1() {
        return new Queue(REPLICA1_WRITE_QUEUE, false, false, true);
    }

    @Bean
    public Queue replicaWriteQueue2() {
        return new Queue(REPLICA2_WRITE_QUEUE, false, false, true);
    }

    @Bean
    public Queue replicaWriteQueue3() {
        return new Queue(REPLICA3_WRITE_QUEUE, false, false, true);
    }

    @Bean
    public Binding replicaQueueBinding1(Queue replicaWriteQueue1, FanoutExchange writeExchange) {
        return BindingBuilder.bind(replicaWriteQueue1).to(writeExchange);
    }

    @Bean
    public Binding replicaQueueBinding2(Queue replicaWriteQueue2, FanoutExchange writeExchange) {
        return BindingBuilder.bind(replicaWriteQueue2).to(writeExchange);
    }

    @Bean
    public Binding replicaQueueBinding3(Queue replicaWriteQueue3, FanoutExchange writeExchange) {
        return BindingBuilder.bind(replicaWriteQueue3).to(writeExchange);
    }

    @Bean
    public Queue replicaReadQueue1() {
        return new Queue(REPLICA1_READ_QUEUE, false, false, true);
    }

    @Bean
    public Queue replicaReadQueue2() {
        return new Queue(REPLICA2_READ_QUEUE, false, false, true);
    }

    @Bean
    public Queue replicaReadQueue3() {
        return new Queue(REPLICA3_READ_QUEUE, false, false, true);
    }

    @Bean
    public Binding replicaReadBinding1(Queue replicaReadQueue1, FanoutExchange readRequestExchange) {
        return BindingBuilder.bind(replicaReadQueue1).to(readRequestExchange);
    }

    @Bean
    public Binding replicaReadBinding2(Queue replicaReadQueue2, FanoutExchange readRequestExchange) {
        return BindingBuilder.bind(replicaReadQueue2).to(readRequestExchange);
    }

    @Bean
    public Binding replicaReadBinding3(Queue replicaReadQueue3, FanoutExchange readRequestExchange) {
        return BindingBuilder.bind(replicaReadQueue3).to(readRequestExchange);
    }
}
