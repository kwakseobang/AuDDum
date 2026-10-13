package org.kwakmunsu.auddum.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.support.config.JpaAuditingConfig;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

/**
 * 엔티티 매핑이 Flyway가 만든 스키마(V1)와 맞는지 확인한다. {@code ddl-auto=validate}가 컬럼 이름·타입 불일치를 잡는다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({JpaAuditingConfig.class, TimeConfig.class})
class PollMappingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Autowired
    EntityManager em;

    @Test
    void 투표를_저장하고_다시_읽으면_값과_선택지_순서가_그대로다() {
        var authorId = insertMember();
        var poll = Poll.create(authorId, "읽씹 3시간 뒤 답장, 괜찮을까?", Category.CONTACT,
            List.of("괜찮다", "별로다", "상황에 따라"), NOW.plusDays(1), NOW);
        em.persist(poll);
        em.flush();
        em.clear();

        var found = em.find(Poll.class, poll.getId());

        assertThat(found.getPublicId().value()).hasSize(12);
        assertThat(found.getQuestion().value()).isEqualTo("읽씹 3시간 뒤 답장, 괜찮을까?");
        assertThat(found.getCategory()).isEqualTo(Category.CONTACT);
        assertThat(found.getStatus()).isEqualTo(PollStatus.PUBLIC);
        assertThat(found.getClosesAt().value()).isEqualTo(NOW.plusDays(1));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getOptions())
            .extracting(option -> option.getPosition().value(), option -> option.getContent().value())
            .containsExactly(
                tuple(1, "괜찮다"),
                tuple(2, "별로다"),
                tuple(3, "상황에 따라"));
    }

    @Test
    void 마감_시각이_없는_투표도_저장되고_마감되지_않는다() {
        var authorId = insertMember();
        var poll = Poll.create(authorId, "질문", Category.ETC, List.of("가", "나"), null, NOW);
        em.persist(poll);
        em.flush();
        em.clear();

        var found = em.find(Poll.class, poll.getId());

        assertThat(found.getClosesAt()).isNull();
        assertThat(found.isClosed(NOW.plusYears(10))).isFalse();
    }

    private Long insertMember() {
        em.createNativeQuery("INSERT INTO member (status, role, created_at, updated_at) VALUES ('ACTIVE', 'MEMBER', :now, :now)")
            .setParameter("now", NOW)
            .executeUpdate();

        return ((Number) em.createNativeQuery("SELECT MAX(id) FROM member").getSingleResult()).longValue();
    }

}
