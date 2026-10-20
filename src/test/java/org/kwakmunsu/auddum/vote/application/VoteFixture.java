package org.kwakmunsu.auddum.vote.application;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.ClosesAt;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.vote.application.dto.PollChoice;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;
import org.springframework.test.util.ReflectionTestUtils;

/*
 * 참여 테스트에서 반복적으로 사용되는 Poll 데이터와 쿠키 조작을 모아둔 클래스
 * */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VoteFixture {

    /** 요청을 받은 서버 시각. 서비스에 now로 넘긴다. */
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0, 0);

    /** 투표를 만든 시각. 마감 시각이 NOW여도 만들 수 있도록 NOW보다 과거다. */
    public static final LocalDateTime CREATED_AT = NOW.minusDays(1);

    /** 투표를 만들 때는 미래여야 하므로, 이미 마감된 투표를 만들 때 필드를 이 값으로 바꾼다. */
    public static final LocalDateTime LONG_PAST = LocalDateTime.of(2000, 1, 1, 0, 0);

    private static final String FORGED_SIGNATURE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    public static Poll openPoll(Long authorId) {
        return poll(authorId, List.of("국밥", "파스타"), null);
    }

    public static Poll openPoll(Long authorId, List<String> options) {
        return poll(authorId, options, null);
    }

    public static Poll pollClosingAt(Long authorId, LocalDateTime closesAt) {
        return poll(authorId, List.of("국밥", "파스타"), closesAt);
    }

    /** 서비스가 시각을 직접 읽으므로, 실제 현재 시각 기준으로 한 시간 뒤에 마감되는 투표(지금은 열려 있다)를 만든다. */
    public static Poll pollClosingInOneHour(Long authorId) {
        return poll(authorId, List.of("국밥", "파스타"), LocalDateTime.now(TimeConfig.KST).plusHours(1));
    }

    public static List<String> fiveOptions() {
        return List.of("하나", "둘", "셋", "넷", "다섯");
    }

    /** 저장소 없이 쓰는 단위 테스트용으로 투표 id(10)와 선택지 id(101, 102, ...)를 채운다. */
    public static Poll withIds(Poll poll) {
        ReflectionTestUtils.setField(poll, "id", 10L);
        for (int index = 0; index < poll.getOptions().size(); index++) {
            ReflectionTestUtils.setField(poll.getOptions().get(index), "id", 101L + index);
        }

        return poll;
    }

    /** 생성 시점에는 과거 마감을 만들 수 없으므로 마감 시각 필드를 직접 먼 과거로 바꿔 마감된 투표로 만든다. */
    public static Poll expire(Poll poll) {
        ReflectionTestUtils.setField(poll, "closesAt", new ClosesAt(LONG_PAST));

        return poll;
    }

    /** 서명 부분만 다른 값으로 바꿔 서명 불일치(위조) 쿠키를 만든다. */
    public static String tamperSignature(String cookieValue) {
        var lastDot = cookieValue.lastIndexOf('.');

        return cookieValue.substring(0, lastDot + 1) + FORGED_SIGNATURE;
    }

    /** 로그인한 회원이 보낸 요청의 신원 단서. 익명 쿠키는 없다. */
    public static VoterCredential member(Long memberId) {
        return new VoterCredential(memberId, null);
    }

    /** 로그인하지 않고 익명 쿠키만 보낸 요청의 신원 단서. */
    public static VoterCredential anonymous(String cookieValue) {
        return new VoterCredential(null, cookieValue);
    }

    /** 로그인도 쿠키도 없는 요청의 신원 단서. */
    public static VoterCredential noCredential() {
        return new VoterCredential(null, null);
    }

    /** 로그인한 회원이 익명 쿠키도 함께 보낸 요청의 신원 단서. */
    public static VoterCredential memberWithCookie(Long memberId, String cookieValue) {
        return new VoterCredential(memberId, cookieValue);
    }

    /** 이 투표의 position번 선택지를 고른다는 요청. */
    public static PollChoice choice(Poll poll, int position) {
        return new PollChoice(poll.getPublicId().value(), position);
    }

    /** 공개 ID로 지정한 투표의 position번 선택지를 고른다는 요청. */
    public static PollChoice choice(String pollPublicId, int position) {
        return new PollChoice(pollPublicId, position);
    }

    private static Poll poll(Long authorId, List<String> options, LocalDateTime closesAt) {
        return Poll.create(authorId, "점심 뭐 먹지?", Category.ETC, options, closesAt, CREATED_AT);
    }

}
