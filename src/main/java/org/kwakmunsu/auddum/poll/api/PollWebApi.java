package org.kwakmunsu.auddum.poll.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.api.dto.PollCreateRequest;
import org.kwakmunsu.auddum.poll.api.dto.PollDetailResponse;
import org.kwakmunsu.auddum.poll.api.dto.PollListRequest;
import org.kwakmunsu.auddum.poll.api.dto.PollListResponse;
import org.kwakmunsu.auddum.poll.application.PollCommandService;
import org.kwakmunsu.auddum.poll.application.PollQueryService;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.CurrentMemberId;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/polls")
@RequiredArgsConstructor
@WebApi
public class PollWebApi extends PollWebApiDocs {

    private final PollCommandService pollCommandService;
    private final PollQueryService pollQueryService;

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

    @Override
    @GetMapping
    public ResponseEntity<PollListResponse> getPolls(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
    ) {
        PollListRequest request = new PollListRequest(cursor, size);
        Slice<Poll> slice = pollQueryService.getPolls(request.cursorId(), request.pageSize());

        return ResponseEntity.ok(PollListResponse.of(slice, LocalDateTime.now(TimeConfig.KST)));
    }

    @Override
    @GetMapping("/{pollId}")
    public ResponseEntity<PollDetailResponse> getPoll(@PathVariable String pollId) {
        Poll poll = pollQueryService.getPoll(pollId);

        return ResponseEntity.ok(PollDetailResponse.of(poll, LocalDateTime.now(TimeConfig.KST)));
    }

}
