package org.kwakmunsu.auddum.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 접근 검증 통합 테스트(H2). 기준은 R-MEMBER-10, 인증-중복-참여 관리자 API 행(DB의 role이 ADMIN이고 정상 상태인 회원만),
 * API-명세과 예외-정책 관리자 권한 행(권한 없음 403 FORBIDDEN, 시도 기록).
 * 로그 내용은 docs가 정하지 않아 "WARN 이상 1건, 시도한 memberId 포함"만 본다.
 * AdminAccessValidator#validateAdmin(Long memberId): 통과하면 반환, 아니면 AuddumException(FORBIDDEN).
 */
@RequiredArgsConstructor
@Transactional
class AdminAccessValidatorTest extends IntegrationTestSupport {

    static final long NOT_EXISTING_MEMBER_ID = 999_999_999L;

    final AdminAccessValidator adminAccessValidator;
    final MemberJpaRepository memberJpaRepository;

    private final Logger adminLogger = (Logger) LoggerFactory.getLogger("org.kwakmunsu.auddum.admin");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        originalLevel = adminLogger.getLevel();
        adminLogger.setLevel(Level.ALL);
        appender.start();
        adminLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        adminLogger.detachAppender(appender);
        appender.stop();
        adminLogger.setLevel(originalLevel);
    }

    @Test
    void 정상_상태의_ADMIN은_통과하고_시도_로그를_남기지_않는다() {
        var adminId = save(Role.ADMIN, MemberStatus.ACTIVE);

        assertThatCode(() -> adminAccessValidator.validateAdmin(adminId)).doesNotThrowAnyException();
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 일반_회원이면_403_FORBIDDEN이고_시도를_WARN으로_남긴다() {
        var memberId = save(Role.MEMBER, MemberStatus.ACTIVE);

        assertForbidden(memberId);
        assertAttemptLogged(memberId);
    }

    @Test
    void 정지된_관리자면_403_FORBIDDEN이고_시도를_WARN으로_남긴다() {
        var suspendedAdminId = save(Role.ADMIN, MemberStatus.SUSPENDED);

        assertForbidden(suspendedAdminId);
        assertAttemptLogged(suspendedAdminId);
    }

    @Test
    void 탈퇴한_관리자면_403_FORBIDDEN이고_시도를_WARN으로_남긴다() {
        var withdrawnAdminId = memberJpaRepository.saveAndFlush(MemberFixture.createWithdrawnAdmin()).getId();

        assertForbidden(withdrawnAdminId);
        assertAttemptLogged(withdrawnAdminId);
    }

    @Test
    void 없는_회원이면_404가_아니라_403_FORBIDDEN이다() {
        assertForbidden(NOT_EXISTING_MEMBER_ID);
        assertAttemptLogged(NOT_EXISTING_MEMBER_ID);
    }

    @Test
    void 회원_id가_null이면_403_FORBIDDEN이다() {
        assertForbidden(null);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    }

    private void assertForbidden(Long memberId) {
        assertThatThrownBy(() -> adminAccessValidator.validateAdmin(memberId))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.FORBIDDEN.getMessage());
    }

    private void assertAttemptLogged(Long memberId) {
        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains(String.valueOf(memberId));
    }

    private Long save(Role role, MemberStatus status) {
        var member = MemberFixture.changeStatus(MemberFixture.createMember(role), status);
        return memberJpaRepository.saveAndFlush(member).getId();
    }

}
