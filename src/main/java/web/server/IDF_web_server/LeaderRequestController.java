package web.server.IDF_web_server;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import web.server.IDF_web_server.service.SearchLogService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class LeaderRequestController {

    private final ZooKeeper zooKeeper;
    private final RestTemplate restTemplate;
    private static final String LEADER_INFO_PATH = "/leader_info";
    private final SearchLogService logService;
    @Autowired
    public LeaderRequestController(ZooKeeper zooKeeper,  SearchLogService logService) {
        this.zooKeeper = zooKeeper;
        this.restTemplate = new RestTemplate();

        this.logService = logService;
    }

    @GetMapping("/search")
    public ResponseEntity<String> handleSearchRequest(@RequestParam String query) throws IOException {
        logService.append(query);
        try {
            // Retrieve the leader's port from the ZooKeeper znode4
            System.out.println("[test] trying get the leader url  ");
            String leaderUrl = new String(zooKeeper.getData(LEADER_INFO_PATH, false, null));
            System.out.println("[test] leader url :  "+ leaderUrl);
            System.out.println("debuuuuuuuuuuuuuuug leader url " + leaderUrl);
            // Construct the leader's URL dynamically
            String api = leaderUrl+"/leader/start";
           // String api = String.format("http://tfidf-node-svc:%s/leader/start", 8085);

            // Forward the search query as a POST request to the leader
            HttpEntity<String> requestEntity = new HttpEntity<>(query);
            String response = restTemplate.postForObject(api, requestEntity, String.class);

            return ResponseEntity.ok(response);
        } catch (KeeperException | InterruptedException e) {
            return ResponseEntity.status(500).body("Error retrieving leader information: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error communicating with leader: " + e.getMessage());
        }
    }




    private static final String REGISTRY_ZNODE = "/service_registry";

    @GetMapping("/zookeeper-data")
    public ResponseEntity<List<String>> getZookeeperData() {
        List<String> znodeDataList = new ArrayList<>();


        try (zooKeeper) {
            List<String> childZnodes = zooKeeper.getChildren(REGISTRY_ZNODE, false);

            for (String child : childZnodes) {
                String childPath = REGISTRY_ZNODE + "/" + child;
                Stat stat = zooKeeper.exists(childPath, false);

                if (stat != null) {
                    byte[] data = zooKeeper.getData(childPath, false, stat);
                    znodeDataList.add(new String(data));
                }
            }

            return ResponseEntity.ok(znodeDataList);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of("Error fetching data from ZooKeeper: " + e.getMessage()));
        }
    }
}
