package org.kwakmunsu.auddum.vote;

import static org.kwakmunsu.auddum.vote.application.VoteFixture.CREATED_AT;

import java.time.LocalDateTime;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 참여 통합 테스트의 공통 저장 헬퍼. 회원·투표·참여 기록을 저장하는 코드를 여기 한 곳에 둔다.
 * 하위 클래스는 생성자 주입을 쓰므로 이 클래스의 저장소만 필드 주입으로 받아 서브클래스 생성자와 충돌하지 않게 한다.
 * 저장소 없이 쓰는 값(투표 데이터, 쿠키 조작)은 {@code VoteFixture}에 있다.
 */
public abstract class VoteIntegrationTestSupport extends IntegrationTestSupport {

    @Autowired
    protected MemberJpaRepository memberJpaRepository;

    @Autowired
    protected PollJpaRepository pollJpaRepository;

    @Autowired
    protected VoteJpaRepository voteJpaRepository;

    protected Long saveMember() {
        return saveMember(Role.MEMBER);
    }

    protected Long saveMember(Role role) {
        return memberJpaRepository.saveAndFlush(MemberFixture.createMember(role)).getId();
    }

    protected Long saveSuspendedMember() {
        var member = MemberFixture.changeStatus(MemberFixture.createMember(), MemberStatus.SUSPENDED);

        return memberJpaRepository.saveAndFlush(member).getId();
    }

    /** 이미 저장된 회원을 정지 상태로 바꾼다. */
    protected void suspend(Long memberId) {
        MemberFixture.changeStatus(memberJpaRepository.findById(memberId).orElseThrow(), MemberStatus.SUSPENDED);
        memberJpaRepository.flush();
    }

    protected Poll savePoll(Poll poll) {
        return pollJpaRepository.saveAndFlush(poll);
    }

    protected void saveVote(Poll poll, int position, Voter voter) {
        saveVote(poll, position, voter, CREATED_AT);
    }

    protected void saveVote(Poll poll, int position, Voter voter, LocalDateTime castAt) {
        voteJpaRepository.saveAndFlush(Vote.cast(poll, position, voter, castAt));
    }

}
