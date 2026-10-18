package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * 숨김·삭제 투표 조회를 실제 필터 체인(액세스 토큰 쿠키 -> 신원 인자 -> 서비스 -> DB)으로 확인한다.
 * API-명세 §3 GET /polls/{pollId}(작성자·관리자 200, 그 밖 404 POLL_NOT_FOUND, 응답 status), §6, 인증-중복-참여 §1(관리자 = DB의 role이 ADMIN이고 탈퇴하지 않은 회원, 토큰 role은 판단에 쓰지 않는다).
 * 규칙의 전수 조합은 PollQueryServiceTest가 맡고, 여기서는 쿠키가 신원으로 이어지는지만 본다.
 * MockMvc 요청은 테스트와 같은 스레드라 @Transactional로 만든 데이터가 요청에서 보이고, 끝나면 롤백된다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class HiddenPollViewChainTest extends IntegrationTestSupport {

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final MemberJpaRepository memberJpaRepository;
    final PollJpaRepository pollJpaRepository;

    @Test
    void 비회원이_공개_투표를_조회하면_200이고_status가_PUBLIC이다() {
        var authorId = saveMember(Role.MEMBER);
        var publicId = savePoll(authorId).getPublicId().value();

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PUBLIC");
    }

    @Test
    void 비회원이_숨김_투표를_조회하면_404_POLL_NOT_FOUND다() {
        var authorId = saveMember(Role.MEMBER);
        var publicId = saveHiddenPoll(authorId).getPublicId().value();

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId).exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
    }

    @Test
    void 무효한_토큰_쿠키는_비회원으로_취급해_숨김_투표가_404다() {
        var authorId = saveMember(Role.MEMBER);
        var publicId = saveHiddenPoll(authorId).getPublicId().value();

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId)
            .cookie(new Cookie(AuthCookies.ACCESS_COOKIE, "not-a-jwt"))
            .exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
    }

    @Test
    void 작성자의_쿠키로_숨김_투표를_조회하면_200이고_status가_HIDDEN이다() {
        var authorId = saveMember(Role.MEMBER);
        var publicId = saveHiddenPoll(authorId).getPublicId().value();

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId).cookie(accessCookie(authorId, Role.MEMBER)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("HIDDEN");
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(publicId);
    }

    @Test
    void 작성자의_쿠키로_삭제_투표를_조회하면_200이고_status가_DELETED다() {
        var authorId = saveMember(Role.MEMBER);
        var poll = savePoll(authorId);
        poll.deleteByAdmin();
        pollJpaRepository.saveAndFlush(poll);

        var result = mvcTester.get().uri("/api/v1/polls/" + poll.getPublicId().value())
            .cookie(accessCookie(authorId, Role.MEMBER))
            .exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DELETED");
    }

    @Test
    void 작성자가_아닌_회원의_쿠키로_숨김_투표를_조회하면_404_POLL_NOT_FOUND다() {
        var authorId = saveMember(Role.MEMBER);
        var otherId = saveMember(Role.MEMBER);
        var publicId = saveHiddenPoll(authorId).getPublicId().value();

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId).cookie(accessCookie(otherId, Role.MEMBER)).exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
    }

    @Test
    void 토큰과_DB가_모두_ADMIN인_관리자의_쿠키로_숨김_투표를_조회하면_200이다() {
        var authorId = saveMember(Role.MEMBER);
        var adminId = saveMember(Role.ADMIN);
        var publicId = saveHiddenPoll(authorId).getPublicId().value();

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId).cookie(accessCookie(adminId, Role.ADMIN)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("HIDDEN");
    }

    @Test
    void 토큰은_MEMBER여도_DB가_ADMIN이면_숨김_투표를_조회해_200이다() {
        var authorId = saveMember(Role.MEMBER);
        var adminId = saveMember(Role.ADMIN);
        var publicId = saveHiddenPoll(authorId).getPublicId().value();

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId).cookie(accessCookie(adminId, Role.MEMBER)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("HIDDEN");
    }

    @Test
    void 토큰은_ADMIN이어도_DB가_MEMBER면_숨김_투표가_404_POLL_NOT_FOUND다() {
        var authorId = saveMember(Role.MEMBER);
        var memberId = saveMember(Role.MEMBER);
        var publicId = saveHiddenPoll(authorId).getPublicId().value();

        var result = mvcTester.get().uri("/api/v1/polls/" + publicId).cookie(accessCookie(memberId, Role.ADMIN)).exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
    }

    private Long saveMember(Role role) {
        var member = MemberFixture.createMember();
        ReflectionTestUtils.setField(member, "role", role);

        return memberJpaRepository.saveAndFlush(member).getId();
    }

    private Poll savePoll(Long authorId) {
        var poll = Poll.create(authorId, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
            List.of("관심 없음", "그냥 바쁜 것"), null, LocalDateTime.now(TimeConfig.KST));

        return pollJpaRepository.saveAndFlush(poll);
    }

    private Poll saveHiddenPoll(Long authorId) {
        var poll = savePoll(authorId);
        poll.hide();

        return pollJpaRepository.saveAndFlush(poll);
    }

    private Cookie accessCookie(Long memberId, Role tokenRole) {
        return new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(memberId, tokenRole, LocalDateTime.now(TimeConfig.KST)));
    }

}
