package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.CREATED_AT;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymous;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.choice;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.member;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.memberWithCookie;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.noCredential;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.tamperSignature;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투표 상세 조회 서비스의 통합 테스트(H2). 기준은 API-명세 §3 GET /polls/{pollId}, §4 결과 공개 규칙,
 * 인증-중복-참여 §2 발급과 읽기, 예외-정책 P-03·P-11·P-25, 설계-결정 §3.
 * 조회는 읽기 전용이라 쿠키를 발급하지 않고 투표 기록도 만들지 않는다.
 * 결과 공개·마감 판단의 진리표와 경계는 PollTest(isResultOpen, isClosed)가 검증한다. 이 클래스는 신원(회원·쿠키)이 참여 여부로
 * 이어지고 그 값이 공개 판단과 응답 조립에 연결되는지만 본다.
 */
@RequiredArgsConstructor
@Transactional
class VoteQueryServiceTest extends VoteIntegrationTestSupport {

    final VoteQueryService voteQueryService;
    final VoteCommandService voteCommandService;
    final AnonCookieProvider anonCookieProvider;

    private final Logger auddumLogger = (Logger) LoggerFactory.getLogger("org.kwakmunsu.auddum");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        originalLevel = auddumLogger.getLevel();
        auddumLogger.setLevel(Level.ALL);
        appender.start();
        auddumLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auddumLogger.detachAppender(appender);
        appender.stop();
        auddumLogger.setLevel(originalLevel);
    }

    // ---- 결과 공개 규칙: 마감 전 (P-03, API-명세 §4 표) ----

    @Test
    void 마감_전에_참여한_회원은_myChoice와_result를_받는다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.member(memberId));
        saveVote(poll, 1, Voter.member(saveMember()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(memberId));

        assertThat(detail.myChoice()).isEqualTo(2);
        assertThat(detail.result().total()).isEqualTo(2);
        assertThat(detail.result().options()).extracting("position", "count")
            .containsExactly(tuple(1, 1L), tuple(2, 1L));
    }

    @Test
    void 마감_전에_참여하지_않은_회원은_result가_null이고_participantCount만_받는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.anonymous(new AnonId(UUID.randomUUID())));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(saveMember()));

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
        assertThat(detail.participantCount()).isEqualTo(3);
        assertThat(detail.poll().getOptions()).hasSize(2);
    }

    @Test
    void 마감_전에_쿠키가_없는_비회원은_미참여라_result가_null이고_participantCount를_받는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), noCredential());

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
        assertThat(detail.participantCount()).isEqualTo(1);
    }

    @Test
    void 마감_전에_참여한_비회원은_유효한_쿠키로_myChoice와_result를_받는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();
        saveVote(poll, 2, Voter.anonymous(cookie.anonId()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), anonymous(cookie.value()));

        assertThat(detail.myChoice()).isEqualTo(2);
        assertThat(detail.result().total()).isEqualTo(1);
    }

    @Test
    void 참여하지_않은_익명_쿠키는_미참여로_보고_result가_null이다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));
        var cookie = anonCookieProvider.issue();

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), anonymous(cookie.value()));

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
        assertThat(detail.participantCount()).isEqualTo(1);
    }

    // ---- 결과 공개 규칙: 마감 후 (P-25) 와 마감 경계 (P-11) ----

    @Test
    void 마감_후에는_참여하지_않은_회원도_result를_받는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.member(saveMember()));
        savePoll(VoteFixture.expire(poll));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(saveMember()));

        assertThat(detail.closed()).isTrue();
        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result().total()).isEqualTo(2);
        assertThat(detail.participantCount()).isEqualTo(2);
    }

    // ---- result와 participantCount의 모양 ----

    @Test
    void 득표가_0인_선택지도_position_순서대로_result에_포함한다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember(), VoteFixture.fiveOptions()));
        saveVote(poll, 3, Voter.member(memberId));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(memberId));

        assertThat(detail.result().options()).extracting("position", "count")
            .containsExactly(tuple(1, 0L), tuple(2, 0L), tuple(3, 1L), tuple(4, 0L), tuple(5, 0L));
    }

    @Test
    void 참여가_하나도_없는_마감_투표는_득표_0의_result와_participantCount_0이다() {
        var poll = savePoll(VoteFixture.expire(VoteFixture.openPoll(saveMember())));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), noCredential());

        assertThat(detail.participantCount()).isZero();
        assertThat(detail.result().total()).isZero();
        assertThat(detail.result().options()).extracting("position", "count")
            .containsExactly(tuple(1, 0L), tuple(2, 0L));
    }

    @Test
    void 공개되는_경우_participantCount는_result의_total과_같다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember(), VoteFixture.fiveOptions()));
        saveVote(poll, 1, Voter.member(memberId));
        saveVote(poll, 4, Voter.member(saveMember()));
        saveVote(poll, 4, Voter.anonymous(new AnonId(UUID.randomUUID())));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(memberId));

        assertThat(detail.participantCount()).isEqualTo(3);
        assertThat(detail.result().total()).isEqualTo(detail.participantCount());
        assertThat(detail.result().options()).extracting("count").containsExactly(1L, 0L, 0L, 2L, 0L);
    }

    // ---- myChoice: 신원 (인증-중복-참여 §2, 설계-결정 §3) ----

    @Test
    void 회원은_자기_참여의_myChoice를_받는다() {
        var firstId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(firstId));
        saveVote(poll, 2, Voter.member(saveMember()));

        assertThat(voteQueryService.getPollDetail(publicIdOf(poll), member(firstId)).myChoice()).isEqualTo(1);
    }

    @Test
    void 다른_회원의_참여는_참여하지_않은_회원의_myChoice에_영향이_없다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));

        assertThat(voteQueryService.getPollDetail(publicIdOf(poll), member(saveMember())).myChoice()).isNull();
    }

    @Test
    void 익명의_참여는_회원의_myChoice에_영향이_없다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.anonymous(anonCookieProvider.issue().anonId()));

        assertThat(voteQueryService.getPollDetail(publicIdOf(poll), member(memberId)).myChoice()).isNull();
    }

    @Test
    void 익명은_자기_쿠키의_참여로_myChoice를_받는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var outsider = anonCookieProvider.issue();
        saveVote(poll, 2, Voter.anonymous(outsider.anonId()));
        saveVote(poll, 1, Voter.member(saveMember()));

        assertThat(voteQueryService.getPollDetail(
            publicIdOf(poll),
            anonymous(outsider.value())).myChoice()).isEqualTo(2);
    }

    @Test
    void 로그인한_회원은_쿠키를_보지_않고_회원_ID로만_참여를_판단한다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();
        saveVote(poll, 1, Voter.anonymous(cookie.anonId()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), memberWithCookie(memberId, cookie.value()));

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
    }

    @Test
    void 회원으로_참여한_기록은_같은_사람의_익명_쿠키로는_보이지_않는다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));
        var cookie = anonCookieProvider.issue();

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), anonymous(cookie.value()));

        assertThat(detail.myChoice()).isNull();
    }

    @Test
    void 같은_익명_쿠키로_다른_투표에_참여한_기록은_이_투표의_myChoice에_영향이_없다() {
        var other = savePoll(VoteFixture.openPoll(saveMember()));
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();
        saveVote(other, 1, Voter.anonymous(cookie.anonId()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), anonymous(cookie.value()));

        assertThat(detail.myChoice()).isNull();
    }

    // ---- 위조·형식 오류·빈 쿠키는 쿠키 없음과 같다 (인증-중복-참여 §2 발급과 읽기, 설계-결정 §3) ----

    @Test
    void 서명이_위조된_쿠키는_오류_없이_미참여로_본다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();
        saveVote(poll, 1, Voter.anonymous(cookie.anonId()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), anonymous(tamperSignature(cookie.value())));

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
        assertThat(detail.participantCount()).isEqualTo(1);
    }

    @Test
    void 형식이_깨진_쿠키는_오류_없이_미참여로_본다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), anonymous("not-a-cookie"));

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
    }

    @Test
    void 빈_문자열_쿠키는_쿠키_없음과_같이_미참여다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), anonymous(""));

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
    }

    @Test
    void 형식이_깨진_쿠키로_조회해도_투표_기록이_늘지_않는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var before = voteJpaRepository.count();

        voteQueryService.getPollDetail(publicIdOf(poll), anonymous("not-a-cookie"));

        assertThat(voteJpaRepository.count()).isEqualTo(before);
    }

    @Test
    void 서명이_위조된_쿠키로_조회해도_투표_기록이_늘지_않는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var before = voteJpaRepository.count();

        voteQueryService.getPollDetail(
            publicIdOf(poll),
            anonymous(tamperSignature(anonCookieProvider.issue().value())));

        assertThat(voteJpaRepository.count()).isEqualTo(before);
    }

    @Test
    void 쿠키_없이_조회해도_투표_기록이_늘지_않는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var before = voteJpaRepository.count();

        voteQueryService.getPollDetail(publicIdOf(poll), noCredential());

        assertThat(voteJpaRepository.count()).isEqualTo(before);
    }

    @Test
    void 마감_후에는_위조_쿠키로도_result를_받는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));
        savePoll(VoteFixture.expire(poll));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), anonymous("not-a-cookie"));

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result().total()).isEqualTo(1);
    }

    // ---- POST 직후 GET 일관성 (API-명세 §4 "클라이언트는 ALREADY_VOTED를 받으면 상세의 myChoice로 맞춘다") ----

    @Test
    void 회원이_참여한_직후_상세의_myChoice와_result는_참여_응답과_같다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));

        var cast = voteCommandService.castVote(choice(publicIdOf(poll), 2), member(memberId));
        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(memberId));

        assertThat(detail.myChoice()).isEqualTo(cast.myChoice());
        assertThat(detail.result()).isEqualTo(cast.result());
        assertThat(detail.participantCount()).isEqualTo(cast.result().total());
    }

    @Test
    void 비회원이_참여해_받은_쿠키로_조회하면_참여_응답과_같은_myChoice와_result다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var before = voteQueryService.getPollDetail(publicIdOf(poll), noCredential());
        var cast = voteCommandService.castVote(choice(publicIdOf(poll), 1), noCredential());
        var after = voteQueryService.getPollDetail(publicIdOf(poll), anonymous(cast.newCookieValue()));

        assertThat(before.myChoice()).isNull();
        assertThat(before.result()).isNull();
        assertThat(after.myChoice()).isEqualTo(1);
        assertThat(after.result()).isEqualTo(cast.result());
    }

    @Test
    void 참여한_뒤_마감되어도_상세의_myChoice는_유지되고_result가_공개된다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.pollClosingInOneHour(saveMember()));
        voteCommandService.castVote(choice(publicIdOf(poll), 2), member(memberId));
        savePoll(VoteFixture.expire(poll));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(memberId));

        assertThat(detail.closed()).isTrue();
        assertThat(detail.myChoice()).isEqualTo(2);
        assertThat(detail.result().total()).isEqualTo(1);
    }

    // ---- 읽기 전용 (인증-중복-참여 §2 "조회만 하는 방문자에게는 발급하지 않는다") ----

    @Test
    void 조회는_투표_기록과_회원을_만들지_않는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var votesBefore = voteJpaRepository.count();
        var membersBefore = memberJpaRepository.count();

        voteQueryService.getPollDetail(publicIdOf(poll), noCredential());

        assertThat(voteJpaRepository.count()).isEqualTo(votesBefore);
        assertThat(memberJpaRepository.count()).isEqualTo(membersBefore);
    }

    // ---- 투표 조회 오류 (API-명세 §3) ----

    @Test
    void 공개_ID_형식이_틀리면_MALFORMED_REQUEST다() {
        assertThatThrownBy(() -> voteQueryService.getPollDetail("short", noCredential()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.MALFORMED_REQUEST.getMessage());
    }

    @Test
    void 없는_투표는_POLL_NOT_FOUND다() {
        assertThatThrownBy(() -> voteQueryService.getPollDetail("abcdefghijkl", noCredential()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    // ---- 숨김·삭제 투표 (API-명세 §3, §4 "숨김 투표의 결과는 일반 사용자에게 노출하지 않는다") ----

    @Test
    void 숨김_투표는_비회원에게_POLL_NOT_FOUND다() {
        var poll = savePoll(hidden(VoteFixture.openPoll(saveMember())));

        assertThatThrownBy(() -> voteQueryService.getPollDetail(publicIdOf(poll), noCredential()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 숨김_투표는_참여했던_일반_회원에게도_POLL_NOT_FOUND이고_결과를_내리지_않는다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));
        poll.hide();
        pollJpaRepository.saveAndFlush(poll);

        assertThatThrownBy(() -> voteQueryService.getPollDetail(publicIdOf(poll), member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 마감된_숨김_투표도_일반_사용자에게는_POLL_NOT_FOUND다() {
        var poll = savePoll(hidden(VoteFixture.expire(VoteFixture.openPoll(saveMember()))));

        assertThatThrownBy(() -> voteQueryService.getPollDetail(publicIdOf(poll), member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제된_투표는_일반_사용자에게_POLL_NOT_FOUND다() {
        var authorId = saveMember();
        var poll = VoteFixture.openPoll(authorId);
        poll.deleteByAuthor(authorId);
        savePoll(poll);

        assertThatThrownBy(() -> voteQueryService.getPollDetail(publicIdOf(poll), member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 작성자는_숨김_투표를_조회할_수_있고_status가_HIDDEN이다() {
        var authorId = saveMember();
        var poll = savePoll(hidden(VoteFixture.openPoll(authorId)));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(authorId));

        assertThat(detail.poll().getStatus()).isEqualTo(PollStatus.HIDDEN);
        assertThat(detail.poll().getPublicId().value()).isEqualTo(publicIdOf(poll));
    }

    @Test
    void 작성자는_삭제된_투표를_조회할_수_있고_status가_DELETED다() {
        var authorId = saveMember();
        var poll = VoteFixture.openPoll(authorId);
        poll.deleteByAuthor(authorId);
        savePoll(poll);

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(authorId));

        assertThat(detail.poll().getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 관리자는_숨김_투표를_조회할_수_있고_status가_HIDDEN이다() {
        var adminId = saveMember(Role.ADMIN);
        var poll = savePoll(hidden(VoteFixture.openPoll(saveMember())));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(adminId));

        assertThat(detail.poll().getStatus()).isEqualTo(PollStatus.HIDDEN);
    }

    @Test
    void 관리자는_관리자가_삭제한_투표를_조회할_수_있고_status가_DELETED다() {
        var adminId = saveMember(Role.ADMIN);
        var poll = VoteFixture.openPoll(saveMember());
        poll.deleteByAdmin();
        savePoll(poll);

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(adminId));

        assertThat(detail.poll().getStatus()).isEqualTo(PollStatus.DELETED);
    }

    @Test
    void 공개_투표의_status는_PUBLIC이다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), noCredential());

        assertThat(detail.poll().getStatus()).isEqualTo(PollStatus.PUBLIC);
    }

    // ---- 정지 회원 (API-명세 §3 "조회는 회원 상태를 확인하지 않는다") ----

    @Test
    void 정지_회원도_상세를_조회할_수_있고_자신의_myChoice를_받는다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.member(memberId));
        suspend(memberId);

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(memberId));

        assertThat(detail.myChoice()).isEqualTo(2);
        assertThat(detail.result()).isNotNull();
    }

    @Test
    void 참여하지_않은_정지_회원도_조회할_수_있고_마감_전에는_result가_null이다() {
        var suspendedId = saveSuspendedMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(suspendedId));

        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
    }

    @Test
    void 정지된_작성자도_자신의_숨김_투표를_조회할_수_있다() {
        var authorId = saveSuspendedMember();
        var poll = savePoll(hidden(VoteFixture.openPoll(authorId)));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(authorId));

        assertThat(detail.poll().getStatus()).isEqualTo(PollStatus.HIDDEN);
    }

    // ---- 숨김·삭제 투표의 참여 정보는 일반 규칙과 같다 (API-명세 §4 결과 공개 규칙, 예외-정책 P-03·P-04·P-25) ----

    @Test
    void 작성자가_미참여로_마감_전_숨김_투표를_보면_result가_null이고_participantCount는_전체_참여_수다() {
        var authorId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(authorId));
        saveVote(poll, 1, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.member(saveMember()));
        savePoll(hidden(poll));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(authorId));

        assertThat(detail.poll().getStatus()).isEqualTo(PollStatus.HIDDEN);
        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result()).isNull();
        assertThat(detail.participantCount()).isEqualTo(3);
    }

    @Test
    void 참여한_작성자가_숨김_투표를_보면_myChoice와_result를_받는다() {
        var authorId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(authorId));
        saveVote(poll, 2, Voter.member(authorId));
        saveVote(poll, 1, Voter.member(saveMember()));
        savePoll(hidden(poll));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(authorId));

        assertThat(detail.myChoice()).isEqualTo(2);
        assertThat(detail.result().total()).isEqualTo(2);
        assertThat(detail.participantCount()).isEqualTo(2);
    }

    @Test
    void 관리자가_마감된_숨김_투표를_보면_미참여여도_result를_받는다() {
        var adminId = saveMember(Role.ADMIN);
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()), CREATED_AT);
        saveVote(poll, 2, Voter.member(saveMember()), CREATED_AT);
        savePoll(hidden(VoteFixture.expire(poll)));

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(adminId));

        assertThat(detail.closed()).isTrue();
        assertThat(detail.myChoice()).isNull();
        assertThat(detail.result().total()).isEqualTo(2);
        assertThat(detail.participantCount()).isEqualTo(2);
    }

    @Test
    void 참여했던_작성자가_삭제된_투표를_보면_myChoice와_result를_받고_투표_기록은_보존된다() {
        var authorId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(authorId));
        saveVote(poll, 1, Voter.member(authorId));
        saveVote(poll, 2, Voter.member(saveMember()));
        poll.deleteByAuthor(authorId);
        savePoll(poll);

        var detail = voteQueryService.getPollDetail(publicIdOf(poll), member(authorId));

        assertThat(detail.myChoice()).isEqualTo(1);
        assertThat(detail.result().total()).isEqualTo(2);
        assertThat(detail.participantCount()).isEqualTo(2);
        assertThat(voteJpaRepository.count()).isEqualTo(2);
    }

    // ---- 조회에서는 위조 쿠키 WARN 로그를 남기지 않는다 (인증-중복-참여 §2 발급과 읽기, 설계-결정 §3) ----

    @Test
    void 서명이_위조된_쿠키로_조회해도_WARN_이상의_로그가_없다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();

        voteQueryService.getPollDetail(publicIdOf(poll), anonymous(tamperSignature(cookie.value())));

        assertNoWarnOrAbove();
    }

    @Test
    void 형식이_깨진_쿠키로_조회해도_WARN_이상의_로그가_없다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        voteQueryService.getPollDetail(publicIdOf(poll), anonymous("garbage-cookie"));

        assertNoWarnOrAbove();
    }

    @Test
    void 다른_키로_서명한_쿠키로_조회해도_로그에_위조_표식이_없다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var forged = "v1." + UUID.randomUUID() + ".forgedsig";

        voteQueryService.getPollDetail(publicIdOf(poll), anonymous(forged));

        assertNoWarnOrAbove();
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
            .noneMatch(message -> message.contains("ANON_COOKIE_FORGED"));
    }

    @Test
    void 회원이_위조_쿠키를_같이_보내도_쿠키를_보지_않아_로그가_없다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        voteQueryService.getPollDetail(publicIdOf(poll), memberWithCookie(memberId, "garbage-cookie"));

        assertNoWarnOrAbove();
    }

    // ---- 보조 ----

    private void assertNoWarnOrAbove() {
        assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    }

    private static String publicIdOf(Poll poll) {
        return poll.getPublicId().value();
    }

    private static Poll hidden(Poll poll) {
        poll.hide();

        return poll;
    }

}
