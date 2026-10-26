package org.kwakmunsu.auddum.report.domain;

/** 신고 사유(도메인-모델 §6). 허용된 다섯 가지뿐이다. */
public enum ReportReasonCode {

    ABUSE_HATE,        // 욕설·혐오 표현
    SEXUAL_HARASSMENT, // 성희롱·성적 수치심 유발
    PERSONAL_INFO,     // 개인정보 노출
    SPAM,              // 스팸·광고
    ETC                // 기타

}
