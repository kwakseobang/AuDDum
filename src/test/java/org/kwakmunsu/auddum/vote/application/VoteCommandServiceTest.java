package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.NOW;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymous;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.choice;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.member;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.memberWithCookie;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.noCredential;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.tamperSignature;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider;
import org.kwakmunsu.auddum.vote.infrastructure.AnonCookieProvider.Verification;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투표 참여 서비스의 통합 테스트(H2). 기준은 API-명세 §4, 인증-중복-참여 §2·§3, 예외-정책 P-08·P-11·P-12.
 * 유니크 위반처럼 트랜잭션 경계가 필요한 시나리오는 VoteWriterConflictTest가 맡는다.
 */
@RequiredArgsConstructor
@Transactional
class VoteCommandServiceTest extends VoteIntegrationTestSupport {

    final VoteCommandService voteCommandService;
    final AnonCookieProvider anonCookieProvider;
    final EntityManager entityManager;

    // ---- 저장 성공과 응답 (API-명세 §4 6단계, 본문 { myChoice, result }) ----

    @Test
    void 회원이_처음_참여하면_내_선택과_결과를_돌려준다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.anonymous(new AnonId(UUID.randomUUID())));

        var result = voteCommandService.castVote(
            choice(poll, 2),
            member(memberId));

        assertThat(result.myChoice()).isEqualTo(2);
        assertThat(result.result().total()).isEqualTo(3);
        assertThat(result.result().options()).extracting("position", "count")
            .containsExactly(tuple(1, 1L), tuple(2, 2L));
        assertThat(result.newCookieValue()).isNull();
    }

    @Test
    void 아무도_고르지_않은_선택지도_득표_0으로_position_순서대로_내려준다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember(), VoteFixture.fiveOptions()));

        var result = voteCommandService.castVote(
            choice(poll, 3),
            member(memberId));

        assertThat(result.result().total()).isEqualTo(1);
        assertThat(result.result().options()).extracting("position", "count")
            .containsExactly(
                tuple(1, 0L),
                tuple(2, 0L),
                tuple(3, 1L),
                tuple(4, 0L),
                tuple(5, 0L)
            );
    }

    @Test
    void 회원_참여는_투표_선택지_회원_ID로_저장된다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        voteCommandService.castVote(choice(poll, 2), member(memberId));
        entityManager.flush();
        entityManager.clear();

        var saved = voteJpaRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPollId()).isEqualTo(poll.getId());
        assertThat(saved.get(0).getOptionId()).isEqualTo(poll.getOptions().get(1).getId());
        assertThat(saved.get(0).getVoter()).isEqualTo(Voter.member(memberId));
    }

    // ---- 신원 결정 (인증-중복-참여 §2, API-명세 §4 쿠키 발급) ----

    @Test
    void 쿠키가_없는_비회원은_새_쿠키를_발급받고_그_UUID로_저장된다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var result = voteCommandService.castVote(choice(poll, 1), noCredential());
        entityManager.flush();
        entityManager.clear();

        assertThat(result.newCookieValue()).isNotBlank();
        var issued = (Verification.Valid) anonCookieProvider.check(result.newCookieValue());
        assertThat(voteJpaRepository.findAll()).extracting(Vote::getVoter)
            .containsExactly(Voter.anonymous(issued.anonId()));
    }

    @Test
    void 유효한_쿠키를_가진_비회원은_쿠키를_새로_발급받지_않고_쿠키의_UUID로_저장된다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();

        var result = voteCommandService.castVote(
            choice(poll, 1),
            anonymous(cookie.value()));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.newCookieValue()).isNull();
        assertThat(voteJpaRepository.findAll()).extracting(Vote::getVoter)
            .containsExactly(Voter.anonymous(cookie.anonId()));
    }

    @Test
    void 위조된_쿠키는_오류_없이_새로_발급하고_위조된_UUID로는_저장되지_않는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var forged = tamperSignature(anonCookieProvider.issue().value());

        var result = voteCommandService.castVote(
            choice(poll, 1),
            anonymous(forged));
        entityManager.flush();
        entityManager.clear();

        assertThat(anonCookieProvider.check(forged)).isInstanceOf(Verification.SignatureMismatch.class);
        assertThat(result.newCookieValue()).isNotBlank().isNotEqualTo(forged);
        var issued = (Verification.Valid) anonCookieProvider.check(result.newCookieValue());
        assertThat(voteJpaRepository.findAll()).extracting(Vote::getVoter)
            .containsExactly(Voter.anonymous(issued.anonId()));
    }

    @Test
    void 형식이_깨진_쿠키도_오류_없이_새로_발급한다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var result = voteCommandService.castVote(
            choice(poll, 1),
            anonymous("garbage"));

        assertThat(result.newCookieValue()).isNotBlank();
    }

    @Test
    void 빈_문자열_쿠키는_쿠키_없음으로_보고_새로_발급한다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var result = voteCommandService.castVote(choice(poll, 1), anonymous(""));

        assertThat(result.newCookieValue()).isNotBlank();
    }

    @Test
    void 회원은_익명_쿠키를_무시하고_회원_ID로만_저장한다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();

        var result = voteCommandService.castVote(
            choice(poll, 1),
            memberWithCookie(memberId, cookie.value()));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.newCookieValue()).isNull();
        assertThat(voteJpaRepository.findAll()).extracting(Vote::getVoter)
            .containsExactly(Voter.member(memberId));
    }

    @Test
    void 회원이_위조_쿠키를_보내도_새_쿠키를_발급하지_않는다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var forged = tamperSignature(anonCookieProvider.issue().value());

        var result = voteCommandService.castVote(
            choice(poll, 1),
            memberWithCookie(memberId, forged));

        assertThat(result.newCookieValue()).isNull();
    }

    @Test
    void 같은_투표에_회원과_비회원은_각각_한_번씩_참여할_수_있다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();

        voteCommandService.castVote(
            choice(poll, 1),
            member(memberId));
        voteCommandService.castVote(
            choice(poll, 1),
            anonymous(cookie.value()));

        var anonId = ((Verification.Valid) anonCookieProvider.check(cookie.value())).anonId();
        assertThat(voteJpaRepository.findAll()).extracting(Vote::getVoter)
            .containsExactlyInAnyOrder(Voter.member(memberId), Voter.anonymous(anonId));
    }

    // ---- 기존 참여 (API-명세 §4 3단계, P-08) ----

    @Test
    void 이미_참여한_회원이_같은_선택을_다시_보내도_ALREADY_VOTED이고_기록은_한_건이다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.member(memberId));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 2),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void 이미_참여한_회원이_다른_선택을_보내면_ALREADY_VOTED이고_기존_선택은_그대로다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.member(memberId));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        entityManager.clear();
        assertThat(voteJpaRepository.findAll()).extracting(Vote::getOptionId)
            .containsExactly(poll.getOptions().get(1).getId());
    }

    @Test
    void 이미_참여한_비회원이_같은_쿠키로_같은_선택을_다시_보내도_ALREADY_VOTED다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();
        saveVote(poll, 1, Voter.anonymous(cookie.anonId()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            anonymous(cookie.value())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void 이미_참여한_비회원이_같은_쿠키로_다른_선택을_보내면_ALREADY_VOTED다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();
        saveVote(poll, 1, Voter.anonymous(cookie.anonId()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 2),
            anonymous(cookie.value())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

    @Test
    void 이미_참여한_사람이_존재하지_않는_선택지를_보내면_기존_참여가_먼저라_ALREADY_VOTED다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 3),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

    // ---- 마감 (API-명세 §4 4단계, P-11, P-12) ----

    @Test
    void 마감_시각_전인_투표에는_참여할_수_있다() {
        var poll = savePoll(VoteFixture.pollClosingInOneHour(saveMember()));
        var memberId = saveMember();

        var result = voteCommandService.castVote(
            choice(poll, 1),
            member(memberId));

        assertThat(result.myChoice()).isEqualTo(1);
        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void 마감된_투표에_기록이_없는_사람이_참여하면_POLL_CLOSED이고_저장되지_않는다() {
        var poll = savePoll(VoteFixture.expire(VoteFixture.openPoll(saveMember())));
        var memberId = saveMember();

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());

        assertThat(voteJpaRepository.count()).isZero();
    }

    @Test
    void 마감된_투표에_기록이_없는_사람이_존재하지_않는_선택지를_보내면_마감이_먼저라_POLL_CLOSED다() {
        var poll = savePoll(VoteFixture.expire(VoteFixture.openPoll(saveMember())));
        var memberId = saveMember();

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 3),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());
    }

    @Test
    void 마감_후_참여_기록이_있는_사람이_같은_선택을_보내면_POLL_CLOSED가_아니라_ALREADY_VOTED다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));
        savePoll(VoteFixture.expire(poll));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

    @Test
    void 마감_후_참여_기록이_있는_사람이_다른_선택을_보내도_POLL_CLOSED가_아니라_ALREADY_VOTED다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));
        savePoll(VoteFixture.expire(poll));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 2),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());
    }

    @Test
    void 마감_시각이_없는_투표는_언제든_참여할_수_있다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatCode(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(saveMember())))
            .doesNotThrowAnyException();
    }

    // ---- 선택지 소속 (API-명세 §4 5단계, §7 INVALID_OPTION) ----

    @Test
    void 선택지가_2개인_투표의_마지막_position_2로_참여할_수_있다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        var result = voteCommandService.castVote(
            choice(poll, 2),
            member(saveMember()));

        assertThat(result.myChoice()).isEqualTo(2);
    }

    @Test
    void 선택지가_2개인_투표에_position_3은_INVALID_OPTION이고_저장되지_않는다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 3),
            member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());

        assertThat(voteJpaRepository.count()).isZero();
    }

    @Test
    void 선택지가_5개인_투표의_position_5로_참여할_수_있다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember(), VoteFixture.fiveOptions()));

        var result = voteCommandService.castVote(
            choice(poll, 5),
            member(saveMember()));

        assertThat(result.myChoice()).isEqualTo(5);
    }

    @Test
    void 선택지가_5개인_투표에_position_6은_INVALID_OPTION이다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember(), VoteFixture.fiveOptions()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 6),
            member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());
    }

    @Test
    void position_0은_INVALID_OPTION이다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 0),
            member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.INVALID_OPTION.getMessage());
    }

    // ---- 투표 조회 (API-명세 §4 1단계) ----

    @Test
    void 없는_투표에는_POLL_NOT_FOUND다() {
        assertThatThrownBy(() -> voteCommandService.castVote(
            choice("abcdefghijkl", 1),
            member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 공개_ID_형식이_틀리면_MALFORMED_REQUEST다() {
        assertThatThrownBy(() -> voteCommandService.castVote(choice("short", 1), member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.MALFORMED_REQUEST.getMessage());
    }

    @Test
    void 숨김_투표에는_POLL_NOT_FOUND다() {
        var poll = VoteFixture.openPoll(saveMember());
        poll.hide();
        savePoll(poll);

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제된_투표에는_POLL_NOT_FOUND다() {
        var authorId = saveMember();
        var poll = VoteFixture.openPoll(authorId);
        poll.deleteByAuthor(authorId);
        savePoll(poll);

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 숨겨진_뒤에는_이미_참여한_사람도_POLL_NOT_FOUND다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));
        poll.hide();
        entityManager.flush();

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 작성자도_숨긴_투표에는_참여할_수_없다() {
        var authorId = saveMember();
        var poll = VoteFixture.openPoll(authorId);
        poll.hide();
        savePoll(poll);

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(authorId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    // ---- 회원 상태 (API-명세 §4 2단계, 인증-중복-참여 §3 3번) ----

    @Test
    void 정지_회원은_MEMBER_SUSPENDED이고_저장되지_않는다() {
        var suspendedId = saveSuspendedMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(suspendedId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());

        assertThat(voteJpaRepository.count()).isZero();
    }

    @Test
    void 정지_회원은_이전_참여_기록이_있어도_MEMBER_SUSPENDED가_먼저다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));
        suspend(memberId);

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 정지_회원이_마감된_투표에_보내도_MEMBER_SUSPENDED가_먼저다() {
        var suspendedId = saveSuspendedMember();
        var poll = savePoll(VoteFixture.expire(VoteFixture.openPoll(saveMember())));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(suspendedId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 정지_회원이_없는_투표에_보내면_투표_조회가_먼저라_POLL_NOT_FOUND다() {
        var suspendedId = saveSuspendedMember();

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice("abcdefghijkl", 1),
            member(suspendedId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 탈퇴한_회원은_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(NOW);
        var withdrawnId = memberJpaRepository.save(withdrawn).getId();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(withdrawnId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 존재하지_않는_회원_ID는_UNAUTHENTICATED다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            member(Long.MAX_VALUE)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

}
