package web.server.IDF_web_server.config;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@org.springframework.context.annotation.Configuration   // fully‑qualified to avoid clash
public class HdfsConfig {

    @Value("${hdfs.uri}")
    private String hdfsUri;

    @Bean(destroyMethod = "close")
    public FileSystem hdfs(@Value("${hdfs.uri}") String uri,
                           @Value("${hadoop.conf.dfs.replication:1}") int repl,
                           @Value("${hadoop.conf.dfs.client.block.write.replace-datanode-on-failure.policy:NEVER}") String replPolicy,
                           @Value("${hadoop.conf.dfs.client.block.write.replace-datanode-on-failure.enable:true}") boolean enableReplace
    ) throws IOException, URISyntaxException {

        Configuration c = new Configuration();
        c.set("fs.defaultFS", uri);
        c.setInt("dfs.replication", repl);
        c.set("dfs.client.block.write.replace-datanode-on-failure.policy", replPolicy);
        c.setBoolean("dfs.client.block.write.replace-datanode-on-failure.enable", enableReplace);
        c.setBoolean("dfs.support.append", true);
        return FileSystem.get(new URI(uri), c);
    }

}
