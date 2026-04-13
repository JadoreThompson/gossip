package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.RequestType;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@Slf4j
@RequiredArgsConstructor
public class JoinClusterService {

    private final ClusterConfig clusterConfig;
    private final MemberList memberList;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() throws InterruptedException, IOException {
        final Thread th = new Thread(runnableWrapper(this::run));
        th.start();
    }

    private Runnable runnableWrapper(final CheckedRunnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    //    @Async
    public void run() throws InterruptedException {
        log.info("Hello world from join cluster service");
        boolean success = false;
        final JoinRequest joinRequest = new JoinRequest(
                RequestType.JOIN,
                clusterConfig.getNodeId(),
                clusterConfig.getAddress(),
                clusterConfig.getIncarnation());

        log.info("Formed request {}", joinRequest);

        try (final HttpClient client = HttpClient.newHttpClient()) {
            while (!success) {
                for (Member member : clusterConfig.getMembers()) {
                    final HttpRequest request = HttpRequest
                            .newBuilder()
                            .uri(URI.create(String.format("http://%s:%s/join", member.getAddress().getHostString(), member.getAddress().getPort())))
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(joinRequest)))
                            .header("Content-Type", "application/json")
                            .build();
                    log.info("Sending join request to member " + member.getNodeId() + ", URI " + request.uri());
                    try {
                        final HttpResponse<?> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            success = true;
                            log.info("Adding all config members to member list");
                            memberList.addAll(clusterConfig.getMembers());
                            break;
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                Thread.sleep(5000L);
            }
        }

        log.info("Successfully joined cluster");
    }

    private interface CheckedRunnable {

        void run() throws IOException, InterruptedException;
    }
}
