package web.server.IDF_web_server.service;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service                       // <-- tells Spring it’s a bean
public class SearchLogService {

    private static final Logger log = LoggerFactory.getLogger(SearchLogService.class);

    private final FileSystem fs;
    private final Path logFile;

    public SearchLogService(FileSystem fs,
                            @Value("${hdfs.logFile}") String logFilePath) {
        this.fs = fs;
        this.logFile = new Path(logFilePath);
    }


    public void append(String query) {

        byte[] line = (query + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        log.debug("[HDFS‑log] Incoming query=\"{}\" ({} bytes)", query, line.length);

        for (int attempt = 1; attempt <= 2; attempt++) {

            long t0 = System.currentTimeMillis();
            try {
                // ---------- existence check -------------------------------------
                boolean exists = fs.exists(logFile);
                log.debug("[HDFS‑log] attempt={} fileExists={} path={}", attempt, exists, logFile);

                // ---------- write -----------------------------------------------
                if (exists) {
                    try (FSDataOutputStream out = fs.append(logFile)) {
                        out.write(line);
                        log.debug("[HDFS‑log] attempt={} APPEND ok ({} ms)",
                                attempt, System.currentTimeMillis() - t0);
                    }
                } else {
                    try (FSDataOutputStream out = fs.create(logFile, true)) {
                        out.write(line);
                        log.info("[HDFS‑log] attempt={} CREATED new log file and wrote first record ({} ms)",
                                attempt, System.currentTimeMillis() - t0);
                    }
                }
                return;        // -------- success, exit loop ---------------------

            } catch (IOException ioe) {
                // ---------- lease‑recovery branch --------------------------------
                if (attempt == 1 && ioe.getMessage() != null
                        && ioe.getMessage().contains("lease recovery")) {

                    log.warn("[HDFS‑log] attempt=1 hit lease‑recovery, forcing recoverLease() and retry",
                            ioe.getMessage());

                    try {
                        ((org.apache.hadoop.hdfs.DistributedFileSystem) fs).recoverLease(logFile);
                    } catch (Exception recEx) {
                        log.error("[HDFS‑log] recoverLease() failed", recEx);
                        break;  // no point in retrying
                    }

                    try { Thread.sleep(2000); } catch (InterruptedException ignore) {}
                    continue;  // go to attempt #2

                }
                // ---------- other I/O errors ------------------------------------
                log.error("[HDFS‑log] attempt={} FAILED to write search log (giving up)",
                        attempt, ioe);
                break;
            }
        }
    }


}
