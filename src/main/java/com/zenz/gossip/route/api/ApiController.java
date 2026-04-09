package com.zenz.gossip.route.api;

import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.api.response.Response;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ApiController {

    private final MemberList memberList;

    @PostMapping("/ping")
    public ResponseEntity<?> ping(@RequestBody PingRequest<?> body) {
        return null;
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinRequest body) {
        final Member member = new Member(body.getNodeId());
        if (memberList.contains(member)) {
            final Response response = new Response();
            response.setMessage("Member list contains member");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        return ResponseEntity.ok().build();
    }
}
