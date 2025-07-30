# Entry Point Web Gateway for Distributed TF-IDF System

This project serves as the RESTful web entry point to a distributed TF-IDF document search system. It handles incoming user requests (search, upload, download), forwards them to the system leader node, and logs all search queries to HDFS for audit and analysis.

---

##  Features

* Central REST API gateway
* Routes requests to the current leader node
* Uploads files and triggers indexing through the leader
* Proxies downloads from workers
* Logs search queries in HDFS
* Configurable via environment or Spring properties

---

##  System Responsibilities

This gateway plays the following roles:

* **Search Routing:** Receives a search query and sends it to the active leader node.
* **Upload Forwarding:** Accepts file uploads and forwards them to the least-loaded worker via the leader.
* **Download Proxy:** Tries to resolve file from local disk, or proxies download requests to a worker.
* **Search Logging:** Stores search query logs persistently in HDFS (configurable path).

---

##  API Endpoints

| Endpoint               | Method | Description                               |
| ---------------------- | ------ | ----------------------------------------- |
| `/api/search?q=...`    | GET    | Perform a search query via leader         |
| `/api/upload`          | POST   | Upload a file (delegates to leader logic) |
| `/api/download?path=…` | GET    | Proxy file download to workers            |

---

##  Project Structure

```text
src/
├── controller/
│   └── LeaderRequestController.java      # REST controller
├── service/
│   └── SearchLogService.java             # HDFS-based logging service
├── config/
│   ├── HdfsConfig.java                   # HDFS configuration bean
│   └── WebConfig.java                    # Web settings (CORS, static paths)
└── IdfWebServerApplication.java          # Spring Boot entry point
```

---

##  Configuration

Ensure the following environment variables or Spring properties are provided:

| Property / Env Var | Description                              |
| ------------------ | ---------------------------------------- |
| `LEADER_URL`       | Full base URL of the current leader node |
| `HDFS_URI`         | HDFS namenode URI                        |
| `HDFS_LOG_DIR`     | HDFS directory to write query logs       |

---

## ☁️ HDFS Integration

Search queries are logged to HDFS using the Hadoop Java API. You must provide a valid Hadoop configuration (`core-site.xml`) in the classpath or file system.

### Example

```yaml
hdfs:
  uri: hdfs://namenode:9000
  logDir: /search-logs
```

---

##  Running the Server

Run with Maven:

```bash
./mvnw spring-boot:run
```

Or build and run a JAR:

```bash
./mvnw package
java -jar target/idf-webserver-*.jar
```

---

##  CORS Configuration

CORS settings are configured via `WebConfig.java` to allow cross-origin requests. You can customize allowed origins or methods as needed.

---

##  Built With

* Java 17
* Spring Boot
* Apache Hadoop HDFS
* RESTful Web APIs

---

## 🛠 Kubernetes Deployment

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: idf-web-server-config
  namespace: kh-pipeline
  labels:
    app: idf-web-server
data:
  application.properties: |
    spring.application.name=IDF_web_server
    server.port=8088

    # Zookeeper
    zookeeper.connection=zookeeper:2181

    # HDFS writer
    hdfs.uri=hdfs://hadoop-hadoop-hdfs-nn.kh-pipeline.svc.cluster.local:9000
    hdfs.logFile=/logs/logs.txt

    hadoop.conf.dfs.replication=1
    hadoop.conf.dfs.client.block.write.replace-datanode-on-failure.policy=NEVER
    hadoop.conf.dfs.client.block.write.replace-datanode-on-failure.enable=true

    hadoop.conf.dfs.client.retry.policy.enabled=true
    hadoop.conf.dfs.client.retry.policy.spec="10000,6,60000,10"

    # Prometheus monitoring
    management.endpoints.web.exposure.include=prometheus
    management.endpoint.prometheus.enabled=true
    management.endpoint.health.show.details=always
```

### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idf-web-server
  namespace: kh-pipeline
spec:
  replicas: 1
  selector:
    matchLabels:
      app: idf-web-server
  template:
    metadata:
      labels:
        app: idf-web-server
    spec:
      containers:
        - name: idf-web-server
          image: 172.29.3.41:5000/search_service:2.7
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8088
          volumeMounts:
            - name: app-props
              mountPath: /workspace/config
              readOnly: true
          env:
            - name: SPRING_CONFIG_ADDITIONAL_LOCATION
              value: file:/workspace/config/
      volumes:
        - name: app-props
          configMap:
            name: idf-web-server-config
```

### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: idf-web-server
  namespace: kh-pipeline
  labels:
    app: idf-web-server
spec:
  type: NodePort
  selector:
    app: idf-web-server
  ports:
    - name: http
      port: 8088
      targetPort: 8088
      nodePort: 30088
```

---

##  Future Improvements

* Real-time log aggregation with Kafka
* Health check endpoint for cluster integration
* API authentication and rate-limiting

---

## 📄 License

kheder khdrhswn32@gmail.com 
