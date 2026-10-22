package org.kwakmunsu.auddum.poll.application.dto;

/**
 * 투표를 조회하는 사람의 신원 단서. 아직 확인 전이며, poll은 익명 쿠키 값의 뜻을 해석하지 않고 참여 정보를 가진 쪽에 그대로 넘긴다.
 *
 * @param memberId        로그인한 회원 ID. 비회원이면 null
 * @param anonCookieValue 익명 식별 쿠키 값. 없으면 null
 */
public record Viewer(Long memberId, String anonCookieValue) {

}
