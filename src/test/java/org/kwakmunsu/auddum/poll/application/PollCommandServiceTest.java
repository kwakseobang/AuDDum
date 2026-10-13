package org.kwakmunsu.auddum.poll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.kwakmunsu.auddum.poll.application.PollFixture.createNewPoll;
import static org.kwakmunsu.auddum.poll.application.PollFixture.질문;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.poll.domain.PublicId;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투표 생성 서비스의 통합 테스트(H2). 작성자는 Member를 저장하고 memberId로 넘긴다.
 * 생성 간격과 동일 질문의 경계는 도메인 테스트(PollTest)가 맡고, 여기서는 직전 투표를 찾아 규칙을 적용하는 흐름만 확인한다.
 */
@RequiredArgsConstructor
@Transactional
class PollCommandServiceTest extends IntegrationTestSupport {

    final PollCommandService pollCommandService;
    final PollJpaRepository pollJpaRepository;
    final MemberJpaRepository memberJpaRepository;
    final EntityManager entityManager;

    @Test
    void 투표를_생성하면_PUBLIC_상태로_작성자와_함께_저장된다() {
        var memberId = 회원을_저장한다();

        var created = pollCommandService.createPoll(createNewPoll(질문, List.of("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"), null), memberId);
        entityManager.flush();
        entityManager.clear();

        var poll = pollJpaRepository.findById(created.getId()).orElseThrow();
        assertThat(poll.getAuthorId()).isEqualTo(memberId);
        assertThat(poll.getStatus()).isEqualTo(PollStatus.PUBLIC);
        assertThat(poll.getQuestion().value()).isEqualTo(질문);
        assertThat(poll.getCategory()).isEqualTo(Category.SOME);
        assertThat(poll.getOptions()).hasSize(3);
    }

    @Test
    void 공개_ID가_이미_있으면_다시_발급해_저장한다() {
        var memberId = 회원을_저장한다();
        var taken = pollCommandService.createPoll(createNewPoll("먼저 만든 질문"), memberId).getPublicId();
        var fresh = PublicId.of("zzzzzzzzzzzz");

        try (var publicIds = mockStatic(PublicId.class, CALLS_REAL_METHODS)) {
            publicIds.when(PublicId::generate).thenReturn(taken, fresh);
            var created = pollCommandService.createPoll(createNewPoll("다른 질문"), 회원을_저장한다());

            assertThat(created.getPublicId()).isEqualTo(fresh);
        }
    }

    @Test
    void 공개_ID가_계속_겹치면_INTERNAL_ERROR다() {
        var memberId = 회원을_저장한다();
        var taken = pollCommandService.createPoll(createNewPoll("먼저 만든 질문"), memberId).getPublicId();

        try (var publicIds = mockStatic(PublicId.class, CALLS_REAL_METHODS)) {
            publicIds.when(PublicId::generate).thenReturn(taken);
            var otherMember = 회원을_저장한다();

            assertThatThrownBy(() -> pollCommandService.createPoll(createNewPoll("다른 질문"), otherMember))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    @Test
    void 직후에_다른_질문으로_연속_생성하면_POLL_RATE_LIMITED이고_저장되지_않는다() {
        var memberId = 회원을_저장한다();
        pollCommandService.createPoll(createNewPoll("첫 번째 질문"), memberId);

        assertThatThrownBy(() -> pollCommandService.createPoll(createNewPoll("두 번째 질문"), memberId))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_RATE_LIMITED.getMessage());

        assertThat(pollJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void 직전_생성이_충분히_오래_전이면_다른_질문을_생성할_수_있다() {
        var memberId = 회원을_저장한다();
        과거_투표를_저장한다(memberId, "예전 질문", LocalDateTime.now().minusDays(30));

        assertThatCode(() -> pollCommandService.createPoll(createNewPoll("새 질문"), memberId))
            .doesNotThrowAnyException();

        assertThat(pollJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void 직전_투표가_삭제되었어도_같은_질문이면_DUPLICATE_POLL이다() {
        var memberId = 회원을_저장한다();
        var deleted = 과거_투표를_저장한다(memberId, 질문, LocalDateTime.now().minusDays(30));
        deleted.deleteByAuthor(memberId);
        entityManager.flush();

        assertThatThrownBy(() -> pollCommandService.createPoll(createNewPoll(질문), memberId))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.DUPLICATE_POLL.getMessage());
    }

    @Test
    void 다른_회원의_직후_생성은_간격_제한을_받지_않는다() {
        pollCommandService.createPoll(createNewPoll("질문 하나"), 회원을_저장한다());

        assertThatCode(() -> pollCommandService.createPoll(createNewPoll("질문 둘"), 회원을_저장한다()))
            .doesNotThrowAnyException();

        assertThat(pollJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void 직전_투표와_같은_질문이면_DUPLICATE_POLL이고_저장되지_않는다() {
        var memberId = 회원을_저장한다();
        과거_투표를_저장한다(memberId, 질문, LocalDateTime.now().minusDays(30));

        assertThatThrownBy(() -> pollCommandService.createPoll(createNewPoll(질문), memberId))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.DUPLICATE_POLL.getMessage());

        assertThat(pollJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void 직전이_아닌_과거_투표와_같은_질문은_생성할_수_있다() {
        var memberId = 회원을_저장한다();
        과거_투표를_저장한다(memberId, 질문, LocalDateTime.now().minusDays(30));
        과거_투표를_저장한다(memberId, "그 다음 질문", LocalDateTime.now().minusDays(29));

        assertThatCode(() -> pollCommandService.createPoll(createNewPoll(질문), memberId))
            .doesNotThrowAnyException();

        assertThat(pollJpaRepository.count()).isEqualTo(3);
    }

    @Test
    void 다른_회원의_투표와_같은_질문은_생성할_수_있다() {
        과거_투표를_저장한다(회원을_저장한다(), 질문, LocalDateTime.now().minusDays(30));

        assertThatCode(() -> pollCommandService.createPoll(createNewPoll(질문), 회원을_저장한다()))
            .doesNotThrowAnyException();

        assertThat(pollJpaRepository.count()).isEqualTo(2);
    }

    private Long 회원을_저장한다() {
        return memberJpaRepository.save(MemberFixture.createMember()).getId();
    }

    /** 감사가 created_at을 현재 시각으로 채우므로, 저장한 뒤 JPQL 벌크 수정으로 과거 시각을 덮어쓴다. */
    private Poll 과거_투표를_저장한다(Long memberId, String question, LocalDateTime createdAt) {
        var poll = Poll.create(memberId, question, Category.ETC, List.of("가", "나"), null, LocalDateTime.now());
        var saved = pollJpaRepository.saveAndFlush(poll);
        entityManager.createQuery("update Poll p set p.createdAt = :createdAt where p.id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", saved.getId())
            .executeUpdate();
        entityManager.clear();

        return pollJpaRepository.findById(saved.getId()).orElseThrow();
    }

}
