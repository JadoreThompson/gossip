package com.zenz.gossip.route.api;

import com.zenz.gossip.config.Config;
import com.zenz.gossip.message.MemberAliveMessage;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.api.request.PongRequest;
import com.zenz.gossip.route.exception.BadRequestException;
import com.zenz.gossip.route.exception.NotFoundException;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import com.zenz.gossip.util.MemberStatus;
import com.zenz.gossip.message.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ApiController {

    private final MemberList memberList;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Message> pendingMessages = new ArrayList<>();

    @PostMapping("/ping")
    public void ping(@RequestBody PingRequest body) throws IOException, InterruptedException {
        final Member member = memberList.get(body.getNodeId());
        if (member == null) {
            throw new NotFoundException("Member not found");
        }

        try (final HttpClient client = HttpClient.newHttpClient()) {
            final PongRequest pongRequest = new PongRequest(Config.nodeId);

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(member.getAddress() + "/pong"))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pongRequest)))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @PostMapping("/pong")
    public void pong(@RequestBody PongRequest body) {
        final Member member = memberList.get(body.getNodeId());
        if (member == null) {
            throw new NotFoundException("Member not found");
        }

        if (member.getStatus() == MemberStatus.SUSPICIOUS) {
            pendingMessages.add(new MemberAliveMessage(member.getNodeId(), member.getIncarnation()));
            member.setStatus(MemberStatus.ALIVE);
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinRequest body) {
        final Member member = new Member(body.getNodeId(), body.getAddress());
        if (memberList.contains(member)) {
            throw new BadRequestException("Member list contains member");
        }

        return ResponseEntity.ok().build();
    }
}
