package org.kwakmunsu.auddum.vote.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.choice;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.expire;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.member;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.noCredential;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.application.VoteCommandService;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /api/v1/polls 목록의 participantCount가 실제 참여 수인지 실제 DB로 확인한다.
 * GET /polls, 집계 쿼리(P-07: 페이지의 투표 묶음을 한 번에 집계). 구현 위치(클래스)에 의존하지 않고 API로만 본다.
 * 목록은 PUBLIC 투표만 내려주므로 삭제된 투표의 실제 참여 수는 상세(GET /polls/{pollId})로 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class PollListParticipantCountChainTest extends VoteIntegrationTestSupport {

    final MockMvcTester mvcTester;
    final VoteCommandService voteCommandService;
    final JwtTokenProvider jwtTokenProvider;
    final JsonMapper jsonMapper;

    @Test
    void 참여가_없는_투표의_participantCount는_0이다() {
        var poll = savePoll(saveMember());

        assertThat(countsByPublicId("/api/v1/polls")).containsEntry(publicId(poll), 0);
    }

    @Test
    void 여러_명이_참여한_투표의_participantCount는_참여_기록_건수다() {
        var poll = savePoll(saveMember());
        voteCommandService.castVote(choice(poll, 1), member(saveMember()));
        voteCommandService.castVote(choice(poll, 2), member(saveMember()));
        voteCommandService.castVote(choice(poll, 1), noCredential());

        assertThat(countsByPublicId("/api/v1/polls")).containsEntry(publicId(poll), 3);
    }

    @Test
    void 참여_수가_다른_여러_투표가_섞인_목록에서_투표별로_정확히_센다() {
        var authorId = saveMember();
        var none = savePoll(authorId);
        var one = savePoll(authorId);
        var three = savePoll(authorId);
        var two = savePoll(authorId);
        voteCommandService.castVote(choice(one, 1), member(saveMember()));
        for (var i = 0; i < 3; i++) {
            voteCommandService.castVote(choice(three, 2), member(saveMember()));
        }
        voteCommandService.castVote(choice(two, 1), noCredential());
        voteCommandService.castVote(choice(two, 2), noCredential());

        var counts = countsByPublicId("/api/v1/polls");

        assertThat(counts)
            .containsEntry(publicId(none), 0)
            .containsEntry(publicId(one), 1)
            .containsEntry(publicId(two), 2)
            .containsEntry(publicId(three), 3);
    }

    @Test
    void 한_사람이_여러_투표에_참여해도_다른_투표의_참여가_섞이지_않는다() {
        var authorId = saveMember();
        var first = savePoll(authorId);
        var second = savePoll(authorId);
        var voterId = saveMember();
        voteCommandService.castVote(choice(first, 1), member(voterId));
        voteCommandService.castVote(choice(second, 1), member(voterId));
        voteCommandService.castVote(choice(second, 2), member(saveMember()));

        var counts = countsByPublicId("/api/v1/polls");

        assertThat(counts).containsEntry(publicId(first), 1).containsEntry(publicId(second), 2);
    }

    @Test
    void 목록에_없는_다른_페이지_투표의_참여는_섞이지_않고_페이지마다_정확히_센다() {
        var authorId = saveMember();
        var oldest = savePoll(authorId);
        var middle = savePoll(authorId);
        var newest = savePoll(authorId);
        voteCommandService.castVote(choice(oldest, 1), member(saveMember()));
        voteCommandService.castVote(choice(oldest, 1), member(saveMember()));
        voteCommandService.castVote(choice(oldest, 1), member(saveMember()));
        voteCommandService.castVote(choice(middle, 2), member(saveMember()));

        var firstPage = readBody("/api/v1/polls?size=2");
        var firstCounts = counts(firstPage);
        var cursor = (String) firstPage.get("nextCursor");
        var secondCounts = counts(readBody("/api/v1/polls?size=2&cursor=" + cursor));

        assertThat(firstCounts).containsOnly(Map.entry(publicId(newest), 0), Map.entry(publicId(middle), 1));
        assertThat(secondCounts).containsOnly(Map.entry(publicId(oldest), 3));
    }

    @Test
    void 마감된_투표도_목록에서_실제_참여_수를_내린다() {
        var poll = pollJpaRepository.saveAndFlush(Poll.create(saveMember(), "점심 뭐 먹지?", Category.ETC,
            List.of("국밥", "파스타"), LocalDateTime.now(TimeConfig.KST).plusSeconds(1), LocalDateTime.now(TimeConfig.KST)));
        voteCommandService.castVote(choice(poll, 1), member(saveMember()));
        voteCommandService.castVote(choice(poll, 2), member(saveMember()));
        pollJpaRepository.saveAndFlush(expire(poll));

        assertThat(countsByPublicId("/api/v1/polls")).containsEntry(publicId(poll), 2);
    }

    @Test
    void 숨김_투표의_참여는_목록에_나오지_않는다() {
        var visible = savePoll(saveMember());
        var hidden = savePoll(saveMember());
        voteCommandService.castVote(choice(hidden, 1), member(saveMember()));
        hidden.hide();
        pollJpaRepository.saveAndFlush(hidden);

        var counts = countsByPublicId("/api/v1/polls");

        assertThat(counts).containsEntry(publicId(visible), 0).doesNotContainKey(publicId(hidden));
    }

    @Test
    void 삭제된_투표의_상세_participantCount는_보존된_실제_참여_수다() {
        var authorId = saveMember();
        var poll = savePoll(authorId);
        voteCommandService.castVote(choice(poll, 1), member(saveMember()));
        voteCommandService.castVote(choice(poll, 2), member(saveMember()));
        poll.deleteByAdmin();
        pollJpaRepository.saveAndFlush(poll);
        var adminId = saveMember(Role.ADMIN);
        var token = new Cookie(AuthCookies.ACCESS_COOKIE, adminTokenOf(adminId));

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId(poll)).cookie(token).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DELETED");
        assertThat(result).bodyJson().extractingPath("$.participantCount").isEqualTo(2);
    }

    private String adminTokenOf(Long memberId) {
        return jwtTokenProvider.createAccessToken(memberId, Role.ADMIN,
            LocalDateTime.now(TimeConfig.KST));
    }

    private Poll savePoll(Long authorId) {
        return savePoll(Poll.create(authorId, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), null,
            LocalDateTime.now(TimeConfig.KST)));
    }

    private static String publicId(Poll poll) {
        return poll.getPublicId().value();
    }

    private Map<String, Object> readBody(String uri) {
        var result = mvcTester.get().uri(uri).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = jsonMapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);

        return body;
    }

    private Map<String, Integer> countsByPublicId(String uri) {
        return counts(readBody(uri));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> counts(Map<String, Object> body) {
        var result = new LinkedHashMap<String, Integer>();
        for (var item : (List<Map<String, Object>>) body.get("items")) {
            result.put((String) item.get("id"), (Integer) item.get("participantCount"));
        }

        return result;
    }

}
