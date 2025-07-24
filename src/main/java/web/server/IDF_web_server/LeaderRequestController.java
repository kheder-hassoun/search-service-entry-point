package web.server.IDF_web_server;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriComponentsBuilder;
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


    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> proxyDownload(
            @RequestParam String path) {

        try {
            /* 1. discover leader */
            String leaderUrl = new String(
                    zooKeeper.getData(LEADER_INFO_PATH, false, null));

            String url = UriComponentsBuilder
                    .fromHttpUrl(leaderUrl + "/leader/download")
                    .queryParam("path", path)
                    .build()
                    .toUriString();

            /* 2. open a streaming GET to the leader */
            ResponseEntity<Resource> leaderResp =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            null,
                            Resource.class);

            if (!leaderResp.getStatusCode().is2xxSuccessful()
                    || leaderResp.getBody() == null) {
                return ResponseEntity.status(leaderResp.getStatusCode()).build();
            }

            /* 3. relay the stream to the client without buffering */
            StreamingResponseBody body = outputStream ->
                    FileCopyUtils.copy(leaderResp.getBody().getInputStream(),
                            outputStream);

            HttpHeaders hdr = new HttpHeaders();
            hdr.putAll(leaderResp.getHeaders());      // copy length, disposition, …

            return new ResponseEntity<>(body, hdr, leaderResp.getStatusCode());

        } catch (KeeperException | InterruptedException e) {
            return ResponseEntity.status(503)
                    .body(out -> out.write(("Leader unavailable: " + e).getBytes()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(out -> out.write(("Download error: " + e).getBytes()));
        }
    }

//---


    @PostMapping("/upload")
    public ResponseEntity<String> proxyUpload(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) return ResponseEntity.badRequest().body("Empty file");
        try {
            // 1. discover leader
            String leaderUrl = new String(
                    zooKeeper.getData(LEADER_INFO_PATH, false, null));

            // 2. prepare multipart
            MultiValueMap<String,Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            });
            HttpEntity<MultiValueMap<String,Object>> req =
                    new HttpEntity<>(body, createMultipartHeaders());

            // 3. forward to leader
            String url = leaderUrl + "/leader/upload";
            ResponseEntity<String> resp =
                    restTemplate.postForEntity(url, req, String.class);

            return ResponseEntity.status(resp.getStatusCode()).body(resp.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Upload proxy error: " + e.getMessage());
        }
    }

    private static HttpHeaders createMultipartHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return h;
    }


    //--



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
