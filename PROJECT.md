# Project: TP CAP - Replication with RabbitMQ (Spring Boot)

## Objective
Build a distributed data replication prototype demonstrating CAP theorem trade-offs. 
We replicate a simple text file across 3 replicas. Clients can write lines (append) and read the last line or all lines with majority voting. 
RabbitMQ is the message broker for communication.

## Technologies
- Java 17
- Spring Boot 3.2.x with spring-boot-starter-amqp (RabbitMQ)
- RabbitMQ server running on localhost:5672
- Maven (pom.xml)

## Components (each runs in its own JVM process)

### 1. Replica (3 instances, profiles: replica1, replica2, replica3)
- Each replica has a data directory (e.g., replica_data/replica1/data.txt) 
- Listens to a fanout exchange "write_exchange" for write messages → appends the line to its local data.txt
- Listens to a shared queue "read_request_queue" for read requests.
- Handles two request types:
  * READ_LAST: returns last line of its local file (message format: "correlationId|lastLine")
  * READ_ALL: returns each line one by one (format: "correlationId|replicaId|line") followed by an end marker (format: "correlationId|END|replicaId")
- Replies go to a temporary queue (replyTo field in the request).

### 2. ClientWriter (profile: writer)
- Sends lines to the fanout exchange "write_exchange" (routing key empty).
- Can be invoked with command-line arguments (one or multiple words = one line) or interactive mode (type lines, "exit" quits).
- After sending, the process exits (or continues in interactive mode).

### 3. ClientReader (profile: reader)
- Sends a READ_LAST request to the shared queue "read_request_queue".
- Request format: "READ_LAST:correlationId:replyQueueName"
- Creates a temporary exclusive queue for replies.
- Listens for the first reply that matches its correlationId.
- Takes the first response (simulates availability over consistency).
- Prints the last line or timeout message if no replica responds.

### 4. ClientReaderV2 (profile: readerv2)
- Sends a READ_ALL request (same queue, format: "READ_ALL:correlationId:replyQueueName")
- Collects all lines from all three replicas (expects each replica to send all its lines + END marker).
- Uses majority voting: for each line number (extracted from the line prefix, e.g., "1 Hello"), among the three values received, picks the one that appears at least twice.
- Prints the final reconciled file content (sorted by line number).
- Waits up to 5 seconds for all replicas to respond, then applies voting.

## RabbitMQ Configuration (Spring Boot)
- Exchange: fanout, name = "write_exchange"
- Shared queue: durable? false, name = "read_request_queue"
- For each replica, an auto-delete queue (anonymous) bound to the fanout exchange.
- For replies, temporary exclusive queues.

## File Structure
src/main/java/com/tp/replication/
  ├── TpCapReplicationApplication.java (main entry, can be empty but needed for Spring Boot)
  ├── config/
  │     └── RabbitMQConfig.java (declare exchange, shared queue)
  ├── utils/
  │     └── FileUtils.java (helper: append, readLastLine, readAllLines, ensureDirectory)
  ├── replica/
  │     └── ReplicaService.java (RabbitMQ listeners for writes and read requests)
  └── client/
        ├── ClientWriter.java (CommandLineRunner, profile "writer")
        ├── ClientReader.java (CommandLineRunner, profile "reader")
        └── ClientReaderV2.java (CommandLineRunner, profile "readerv2")

src/main/resources/application.yml (define replica profiles: replica1, replica2, replica3 with properties replica.id and replica.data-dir)

## Running the Application
1. Build: mvn clean package
2. Start RabbitMQ server.
3. Run three replicas in separate terminals:
   java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=replica1
   java -jar ... replica2
   java -jar ... replica3
4. Run writer: 
   java -jar ... --spring.profiles.active=writer "1 First line"
5. Run reader: 
   java -jar ... --spring.profiles.active=reader
6. Run readerv2:
   java -jar ... --spring.profiles.active=readerv2

## Failure Scenario Testing
- Start all replicas, write a few lines.
- Stop replica2 (Ctrl+C).
- Write more lines.
- Restart replica2 → data inconsistency.
- Run ClientReaderV2 → shows majority lines (the lines from replicas 1 and 3 win).

## Important Notes
- All operations are asynchronous (eventual consistency).
- READ_ALL is line-by-line transmission; client must aggregate until END from each replica.
- Majority voting is based on line numbers parsed from the line content (lines stored as "number text...").
- The code must be fully functional, with proper error handling and logging.
- Use Spring AMQP's RabbitTemplate and @RabbitListener annotations.
