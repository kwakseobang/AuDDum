package org.kwakmunsu.auddum.report.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.report.api.dto.ReportSubmitRequest;
import org.kwakmunsu.auddum.report.application.ReportCommandService;
import org.kwakmunsu.auddum.report.application.dto.ReporterCredential;
import org.kwakmunsu.auddum.report.application.dto.SubmitResult;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.AnonCookieName;
import org.kwakmunsu.auddum.support.web.AnonCookies;
import org.kwakmunsu.auddum.support.web.OptionalMemberId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/polls")
@RequiredArgsConstructor
@WebApi
public class ReportWebApi extends ReportWebApiDocs {

    private final ReportCommandService reportCommandService;
    private final AnonCookies anonCookies;

    @Override
    @PostMapping("/{pollId}/reports")
    public ResponseEntity<Void> submitReport(
        @PathVariable String pollId,
        @OptionalMemberId Long memberId,
        @CookieValue(name = AnonCookieName.VALUE, required = false) String anonCookieValue,
        @RequestBody @Valid ReportSubmitRequest request,
        HttpServletResponse response
    ) {
        SubmitResult submitted = reportCommandService.submit(
            pollId,
            request.toReason(),
            new ReporterCredential(memberId, anonCookieValue)
        );

        if (submitted.newCookieValue() != null) {
            anonCookies.write(response, submitted.newCookieValue());
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
