package org.kwakmunsu.auddum.vote.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.OptionalMemberId;
import org.kwakmunsu.auddum.vote.api.dto.PollDetailResponse;
import org.kwakmunsu.auddum.vote.api.dto.VoteCastRequest;
import org.kwakmunsu.auddum.vote.api.dto.VoteCastResponse;
import org.kwakmunsu.auddum.vote.application.VoteCommandService;
import org.kwakmunsu.auddum.vote.application.VoteQueryService;
import org.kwakmunsu.auddum.vote.application.dto.CastResult;
import org.kwakmunsu.auddum.vote.application.dto.PollChoice;
import org.kwakmunsu.auddum.vote.application.dto.PollDetail;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/polls")
@RequiredArgsConstructor
@WebApi
public class VoteWebApi extends VoteWebApiDocs {

    private final VoteCommandService voteCommandService;
    private final VoteQueryService voteQueryService;
    private final AnonCookies anonCookies;

    @Override
    @PostMapping("/{pollId}/votes")
    public ResponseEntity<VoteCastResponse> castVote(
        @PathVariable String pollId,
        @OptionalMemberId Long memberId,
        @CookieValue(name = AnonCookies.ANON_COOKIE, required = false) String anonCookieValue,
        @RequestBody @Valid VoteCastRequest request,
        HttpServletResponse response
    ) {
        CastResult cast = voteCommandService.castVote(
            new PollChoice(pollId, request.position()),
            new VoterCredential(memberId, anonCookieValue)
        );
        if (cast.newCookieValue() != null) {
            anonCookies.write(response, cast.newCookieValue());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(VoteCastResponse.from(cast));
    }

    @Override
    @GetMapping("/{pollId}")
    public ResponseEntity<PollDetailResponse> getPoll(
        @PathVariable String pollId,
        @OptionalMemberId Long memberId,
        @CookieValue(name = AnonCookies.ANON_COOKIE, required = false) String anonCookieValue
    ) {
        PollDetail detail = voteQueryService.getPollDetail(pollId, new VoterCredential(memberId, anonCookieValue));

        return ResponseEntity.ok(PollDetailResponse.from(detail));
    }

}
