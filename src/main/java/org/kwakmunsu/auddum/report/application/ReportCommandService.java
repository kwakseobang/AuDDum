package org.kwakmunsu.auddum.report.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.PollReader;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.application.dto.ReporterCredential;
import org.kwakmunsu.auddum.report.application.dto.SubmitResult;
import org.kwakmunsu.auddum.report.domain.ReportReason;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

/** 신고 접수(API-명세 §5). 서비스는 처리 순서만 잇고, 규칙의 실행은 구현 클래스에 시킨다. */
@RequiredArgsConstructor
@CommandService
public class ReportCommandService {

    private final PollReader pollReader;
    private final ReporterResolver reporterResolver;
    private final ReportValidator reportValidator;
    private final ReportWriter reportWriter;

    /**
     * 투표를 신고한다. 공개 투표 조회(없거나 숨김·삭제면 404, 형식 오류 400) → 신고자 결정(정지 403, 탈퇴·없음 401)
     * → 중복 확인(409) → 저장 순서다. 마감된 투표도 신고할 수 있고, 투표의 노출 상태는 바꾸지 않는다.
     *
     * @param pollPublicId 신고할 투표의 공개 ID
     * @param reason       신고 사유와 상세
     * @param credential   신고하려는 사람의 신원 단서(회원 ID, 익명 쿠키)
     */
    public SubmitResult submit(String pollPublicId, ReportReason reason, ReporterCredential credential) {
        Poll poll = pollReader.getPublic(pollPublicId);

        ResolvedReporter resolved = reporterResolver.resolve(credential);

        reportValidator.validateNotReported(poll, resolved.reporter());
        reportWriter.submit(poll, resolved.reporter(), reason);

        return SubmitResult.of(resolved.newCookieValue());
    }

}
