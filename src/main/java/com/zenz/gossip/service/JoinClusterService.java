package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.util.Member;
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
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class JoinClusterService {

    private final ClusterConfig clusterConfig;

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() throws InterruptedException {
        boolean success = false;
        final JoinRequest joinRequest = new JoinRequest(
                clusterConfig.getNodeId(),
                clusterConfig.getAddress(),
                clusterConfig.getIncarnation());

        try (final HttpClient client = HttpClient.newHttpClient()) {
            while (!success) {
                try{
                    for (Member member : clusterConfig.getMembers()) {
                        final HttpRequest request = HttpRequest
                                .newBuilder()
                                .uri(URI.create(String.format("http://%s/join", member.getAddress())))
                                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(joinRequest)))
                                .build();
                        final HttpResponse<?> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            success = true;
                            break;
                        }
                    }
                } catch (IOException  |InterruptedException e) {
                    e.printStackTrace();
                }

                Thread.sleep(5000L);
            }
        }
    }
}
