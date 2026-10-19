package org.kwakmunsu.auddum.vote;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.JpaAuditingConfig;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

/**
 * 참여 엔티티 매핑이 Flyway가 만든 스키마(V1, V2)와 맞는지 확인한다. {@code ddl-auto=validate}가 컬럼 이름·타입 불일치를 잡고,
 * 참여자가 회원 ID와 익명 식별자 중 한 컬럼에만 저장되는지 원본 컬럼 값으로 확인한다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({JpaAuditingConfig.class, TimeConfig.class})
class VoteMappingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Autowired
    EntityManager em;

    @Test
    void 회원의_참여를_저장하고_다시_읽으면_참여자가_그대로이고_익명_컬럼은_비어_있다() {
        var memberId = insertMember();
        var poll = persistPoll(memberId);

        var vote = Vote.cast(poll, 2, Voter.member(memberId), NOW);
        em.persist(vote);
        em.flush();
        em.clear();

        var found = em.find(Vote.class, vote.getId());

        assertThat(found.getVoter()).isEqualTo(Voter.member(memberId));
        assertThat(found.getPollId()).isEqualTo(poll.getId());
        assertThat(found.getOptionId()).isEqualTo(poll.getOptions().get(1).getId());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(rawColumn("member_id", vote.getId())).isEqualTo(memberId);
        assertThat(rawColumn("anon_id", vote.getId())).isNull();
    }

    @Test
    void 익명의_참여를_저장하고_다시_읽으면_UUID가_36자_문자열로_저장되고_회원_컬럼은_비어_있다() {
        var poll = persistPoll(insertMember());
        var uuid = UUID.randomUUID();

        var vote = Vote.cast(poll, 1, Voter.anonymous(new AnonId(uuid)), NOW);
        em.persist(vote);
        em.flush();
        em.clear();

        var found = em.find(Vote.class, vote.getId());

        assertThat(found.getVoter()).isEqualTo(Voter.anonymous(new AnonId(uuid)));
        assertThat(rawColumn("anon_id", vote.getId())).isEqualTo(uuid.toString());
        assertThat(rawColumn("member_id", vote.getId())).isNull();
    }

    private Poll persistPoll(Long authorId) {
        var poll = Poll.create(authorId, "점심 뭐 먹지?", Category.ETC, List.of("국밥", "파스타"), null, NOW);
        em.persist(poll);
        em.flush();

        return poll;
    }

    private Long insertMember() {
        em.createNativeQuery("INSERT INTO member (status, role, created_at, updated_at) VALUES ('ACTIVE', 'MEMBER', :now, :now)")
            .setParameter("now", NOW)
            .executeUpdate();

        return ((Number) em.createNativeQuery("SELECT MAX(id) FROM member").getSingleResult()).longValue();
    }

    private Object rawColumn(String column, Long voteId) {
        var value = em.createNativeQuery("SELECT " + column + " FROM vote WHERE id = :id")
            .setParameter("id", voteId)
            .getSingleResult();

        return value instanceof Number number ? Long.valueOf(number.longValue()) : value;
    }

}
