package org.kwakmunsu.auddum.poll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투표 목록·상세 조회 서비스의 통합 테스트(H2). 공개 범위, 최신순 커서 이어짐, 상세 선택지 순서와 404를 확인한다.
 * 커서 인코딩과 size 보정은 api 계층 코드(PollListRequest, PollCursor)가 맡는다.
 */
@RequiredArgsConstructor
@Transactional
class PollQueryServiceTest extends IntegrationTestSupport {

    final PollQueryService pollQueryService;
    final PollJpaRepository pollJpaRepository;
    final MemberJpaRepository memberJpaRepository;
    final EntityManager entityManager;

    // ---- 목록 ----

    @Test
    void 목록은_공개_투표만_내려준다() {
        var publicPoll = savePoll("공개 투표");
        var hiddenPoll = savePoll("숨김 투표");
        var deletedPoll = savePoll("삭제 투표");
        hiddenPoll.hide();
        deletedPoll.deleteByAdmin();
        entityManager.flush();
        entityManager.clear();

        var slice = pollQueryService.getPolls(null, 20);

        assertThat(slice.getContent()).extracting(Poll::getId).containsExactly(publicPoll.getId());
    }

    @Test
    void 마감된_투표도_목록에_포함한다() {
        var closedPoll = savePoll("마감된 투표");
        closePoll(closedPoll.getId());
        var openPoll = savePoll("진행 중 투표");

        var slice = pollQueryService.getPolls(null, 20);

        assertThat(slice.getContent()).extracting(Poll::getId).containsExactly(openPoll.getId(), closedPoll.getId());
        assertThat(slice.getContent().get(1).isClosed(LocalDateTime.now(TimeConfig.KST))).isTrue();
    }

    @Test
    void 목록은_최신순이다() {
        var first = savePoll("첫째");
        var second = savePoll("둘째");
        var third = savePoll("셋째");

        var slice = pollQueryService.getPolls(null, 20);

        assertThat(slice.getContent()).extracting(Poll::getId)
            .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void 목록_항목은_생성_시각을_담는다() {
        var poll = savePoll("예전 투표");
        var pastCreatedAt = LocalDateTime.of(2026, 10, 12, 13, 0);
        changeCreatedAt(poll.getId(), pastCreatedAt);

        var slice = pollQueryService.getPolls(null, 20);

        assertThat(slice.getContent().getFirst().getCreatedAt()).isEqualTo(pastCreatedAt);
    }

    @Test
    void 투표가_없으면_빈_목록이고_다음_페이지가_없다() {
        var slice = pollQueryService.getPolls(null, 20);

        assertThat(slice.getContent()).isEmpty();
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    void size만큼_내려주고_더_있으면_hasNext다() {
        savePoll(21);

        var slice = pollQueryService.getPolls(null, 20);

        assertThat(slice.getContent()).hasSize(20);
        assertThat(slice.hasNext()).isTrue();
    }

    @Test
    void size가_50이면_50개를_내려준다() {
        savePoll(51);

        var slice = pollQueryService.getPolls(null, 50);

        assertThat(slice.getContent()).hasSize(50);
        assertThat(slice.hasNext()).isTrue();
    }

    @Test
    void size가_1이면_한_개와_hasNext를_내려준다() {
        savePoll(3);

        var slice = pollQueryService.getPolls(null, 1);

        assertThat(slice.getContent()).hasSize(1);
        assertThat(slice.hasNext()).isTrue();
    }

    @Test
    void 남은_개수가_size와_같으면_hasNext가_아니다() {
        savePoll(2);

        var slice = pollQueryService.getPolls(null, 2);

        assertThat(slice.getContent()).hasSize(2);
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    void 마지막_id를_넘기면_이어서_내려주고_마지막에_hasNext가_아니다() {
        var saved = savePoll(5);
        var newestFirstIds = new ArrayList<>(saved.reversed().stream().map(Poll::getId).toList());

        var firstPage = pollQueryService.getPolls(null, 2);
        var secondPage = pollQueryService.getPolls(firstPage.getContent().getLast().getId(), 2);
        var thirdPage = pollQueryService.getPolls(secondPage.getContent().getLast().getId(), 2);

        assertThat(firstPage.getContent()).extracting(Poll::getId).containsExactlyElementsOf(newestFirstIds.subList(0, 2));
        assertThat(secondPage.getContent()).extracting(Poll::getId).containsExactlyElementsOf(newestFirstIds.subList(2, 4));
        assertThat(thirdPage.getContent()).extracting(Poll::getId).containsExactly(newestFirstIds.get(4));
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.hasNext()).isTrue();
        assertThat(thirdPage.hasNext()).isFalse();
    }

    @Test
    void 커서_이후_숨김_처리된_투표는_건너뛰고_이어진다() {
        var first = savePoll("첫째");
        var second = savePoll("둘째");
        var third = savePoll("셋째");
        var firstPage = pollQueryService.getPolls(null, 1);
        second.hide();
        entityManager.flush();
        entityManager.clear();

        var secondPage = pollQueryService.getPolls(firstPage.getContent().getLast().getId(), 1);

        assertThat(firstPage.getContent()).extracting(Poll::getId).containsExactly(third.getId());
        assertThat(secondPage.getContent()).extracting(Poll::getId).containsExactly(first.getId());
        assertThat(secondPage.hasNext()).isFalse();
    }

    // ---- 상세 ----

    @Test
    void 공개_투표_상세는_선택지를_position_순서로_내려준다() {
        var poll = savePoll("상세 투표", List.of("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"));
        entityManager.flush();
        entityManager.clear();

        var found = pollQueryService.getPoll(poll.getPublicId().value());

        assertThat(found.getId()).isEqualTo(poll.getId());
        assertThat(found.getOptions()).extracting(o -> o.getPosition().value()).containsExactly(1, 2, 3);
        assertThat(found.getOptions()).extracting(o -> o.getContent().value())
            .containsExactly("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함");
    }

    @Test
    void 마감된_투표도_상세_조회된다() {
        var poll = savePoll("마감된 투표");
        closePoll(poll.getId());

        var found = pollQueryService.getPoll(poll.getPublicId().value());

        assertThat(found.isClosed(LocalDateTime.now(TimeConfig.KST))).isTrue();
    }

    @Test
    void 숨김_투표_상세는_POLL_NOT_FOUND다() {
        var poll = savePoll("숨김 투표");
        poll.hide();
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> pollQueryService.getPoll(poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제_투표_상세는_POLL_NOT_FOUND다() {
        var poll = savePoll("삭제 투표");
        poll.deleteByAdmin();
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> pollQueryService.getPoll(poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 없는_공개_ID_상세는_POLL_NOT_FOUND다() {
        assertThatThrownBy(() -> pollQueryService.getPoll("zzzzzzzzzzzz"))
            .isInstanceOf(AuddumException.class)
            .extracting(e -> ((AuddumException) e).getErrorCode())
            .isEqualTo(PollErrorCode.POLL_NOT_FOUND);
    }

    // ---- 보조 ----

    private Long authorId = null;

    private Long getAuthorId() {
        if (authorId == null) {
            authorId = memberJpaRepository.save(MemberFixture.createMember()).getId();
        }

        return authorId;
    }

    private Poll savePoll(String question) {
        return savePoll(question, List.of("가", "나"));
    }

    private Poll savePoll(String question, List<String> options) {
        var poll = Poll.create(getAuthorId(), question, Category.ETC, options, null, LocalDateTime.now(TimeConfig.KST));

        return pollJpaRepository.saveAndFlush(poll);
    }

    /** 오래된 순(저장 순서)으로 돌려준다. */
    private List<Poll> savePoll(int count) {
        var polls = new ArrayList<Poll>();
        for (int i = 1; i <= count; i++) {
            polls.add(savePoll("질문 " + i));
        }
        entityManager.clear();

        return polls;
    }

    /** 생성 시점에는 과거 마감을 만들 수 없으므로, 저장한 뒤 JPQL 벌크 수정으로 마감 시각을 과거로 덮어쓴다. */
    private void closePoll(Long pollId) {
        entityManager.createQuery("update Poll p set p.closesAt.value = :closesAt where p.id = :id")
            .setParameter("closesAt", LocalDateTime.now(TimeConfig.KST).minusDays(1))
            .setParameter("id", pollId)
            .executeUpdate();
        entityManager.clear();
    }

    /** 감사가 created_at을 현재 시각으로 채우므로, 저장한 뒤 JPQL 벌크 수정으로 덮어쓴다. */
    private void changeCreatedAt(Long pollId, LocalDateTime createdAt) {
        entityManager.createQuery("update Poll p set p.createdAt = :createdAt where p.id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", pollId)
            .executeUpdate();
        entityManager.clear();
    }

}
