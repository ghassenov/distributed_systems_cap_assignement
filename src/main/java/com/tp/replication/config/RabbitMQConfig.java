package com.tp.replication.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitMQConfig {

    public static final String WRITE_EXCHANGE = "write_exchange";
    public static final String READ_REQUEST_EXCHANGE = "read_request_exchange";

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

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Configuration
    @Profile({"replica1", "replica2", "replica3"})
    static class ReplicaQueuesConfig {

        @Bean
        public Queue replicaWriteQueue(@Value("${replica.write-queue}") String queueName) {
            return QueueBuilder.nonDurable(queueName).autoDelete().build();
        }

        @Bean
        public Queue replicaReadQueue(@Value("${replica.read-queue}") String queueName) {
            return QueueBuilder.nonDurable(queueName).autoDelete().build();
        }

        @Bean
        public Binding replicaWriteBinding(Queue replicaWriteQueue, FanoutExchange writeExchange) {
            return BindingBuilder.bind(replicaWriteQueue).to(writeExchange);
        }

        @Bean
        public Binding replicaReadBinding(Queue replicaReadQueue, FanoutExchange readRequestExchange) {
            return BindingBuilder.bind(replicaReadQueue).to(readRequestExchange);
        }
    }
}
