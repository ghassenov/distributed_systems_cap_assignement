# TP CAP - Replication with RabbitMQ (Spring Boot)

Distributed data replication prototype demonstrating CAP theorem trade-offs using Spring Boot 3.2 and RabbitMQ. Three replicas append lines to local files; clients can read the last line or reconcile the full file using majority voting.

## Requirements

- Java 17+
- Maven 3.6+
- RabbitMQ on localhost:5672

## Build

```bash
mvn clean package -DskipTests
```

## Run

Start three replicas in separate terminals:

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=replica1
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=replica2
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=replica3
```

Write one line (or use interactive mode with no args):

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=writer "First line"
```

Read last line (availability-first):

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=reader
```

Read all lines with majority voting:

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=readerv2
```

## Message Formats

- Write: any text line
- READ_LAST request: `READ_LAST:<correlationId>:<replyQueueName>`
- READ_LAST response: `<correlationId>|<lastLine>`
- READ_ALL request: `READ_ALL:<correlationId>:<replyQueueName>`
- READ_ALL response (per line): `<correlationId>|<replicaId>|<line>`
- READ_ALL end marker: `<correlationId>|END|<replicaId>`

## RabbitMQ Topology

- `write_exchange` (fanout) broadcasts writes to all replicas.
- `read_request_exchange` (fanout) broadcasts read requests to all replicas.
- Each replica has its own read/write queues bound to the exchanges.

## Failure Scenario

1. Start all replicas and write a few lines.
2. Stop `replica2`.
3. Write more lines.
4. Restart `replica2` (it will be inconsistent).
5. Run `readerv2` to reconcile via majority voting.

## Test Scenarios

Each scenario includes the commands to run and the expected changes in the replica data files.

### Scenario 1: Basic Replication (All replicas up)

Run (in separate terminals):

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=replica1
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=replica2
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=replica3
```

Write a few lines:

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=writer "First line"
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=writer "Second line"
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=writer "Third line"
```

Expected file changes:

- [replica_data/replica1/data.txt](replica_data/replica1/data.txt) contains lines 1-3.
- [replica_data/replica2/data.txt](replica_data/replica2/data.txt) contains lines 1-3.
- [replica_data/replica3/data.txt](replica_data/replica3/data.txt) contains lines 1-3.

### Scenario 2: READ_LAST (Availability-first)

Run:

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=reader
```

Expected file changes:

- No file changes. The client prints the last line from the first available replica.

### Scenario 3: Replica Failure and Divergence

Stop replica2 (Ctrl+C in its terminal), then write new lines:

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=writer "After failure"
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=writer "More data"
```

Expected file changes:

- [replica_data/replica1/data.txt](replica_data/replica1/data.txt) appends lines 4-5.
- [replica_data/replica2/data.txt](replica_data/replica2/data.txt) does not append lines 4-5 (replica2 was down).
- [replica_data/replica3/data.txt](replica_data/replica3/data.txt) appends lines 4-5.

Restart replica2:

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=replica2
```

Expected file changes:

- [replica_data/replica2/data.txt](replica_data/replica2/data.txt) remains missing lines 4-5, demonstrating inconsistency.

### Scenario 4: Majority Voting (READ_ALL)

Run:

```bash
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=readerv2
```

Expected file changes:

- No file changes. The client prints a reconciled view using majority voting by line position, keeping the value seen in at least two replicas.

### Scenario 5: Manual Divergence and Reconcile

Manually introduce a conflicting line in replica2, then reconcile:

```bash
echo "Different version" >> replica_data/replica2/data.txt
java -jar target/tp-cap-replication-1.0-SNAPSHOT.jar --spring.profiles.active=readerv2
```

Expected file changes:

- [replica_data/replica2/data.txt](replica_data/replica2/data.txt) contains the conflicting line.
- The majority result should still favor the value present in at least two replicas for the affected line position.

## Configuration

Replica IDs and data directories are set per profile in `application.yml`. Timeouts and replica count are configurable using:

- `client.read-timeout-ms`
- `client.read-all-timeout-ms`
- `replica.count`