package org.kwakmunsu.auddum.poll.api;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.api.dto.PollCreateRequest;
import org.kwakmunsu.auddum.poll.application.PollCommandService;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.CurrentMemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/polls")
@RequiredArgsConstructor
@WebApi
public class PollWebApi extends PollWebApiDocs {

    private final PollCommandService pollCommandService;

    @Override
    @PostMapping
    public ResponseEntity<String> createPoll(
        @CurrentMemberId Long memberId,
        @RequestBody @Valid PollCreateRequest request
    ) {
        Poll poll = pollCommandService.createPoll(request.toNewPoll(), memberId);
        String publicId = poll.getPublicId().value();

        return ResponseEntity.created(URI.create("/api/v1/polls/" + publicId)).body(publicId);
    }

}
