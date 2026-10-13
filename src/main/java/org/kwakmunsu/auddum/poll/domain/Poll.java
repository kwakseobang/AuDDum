package org.kwakmunsu.auddum.poll.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.support.jpa.BaseEntity;

/**
 * 투표 애그리거트 루트. 질문·선택지·마감은 생성 후 수정할 수 없고, 마감 판단과 상태 전이는 이 객체가 맡는다.
 */
@Table(name = "poll")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Poll extends BaseEntity {

    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 5;

    @Embedded
    private PublicId publicId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Embedded
    private PollQuestion question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PollStatus status;

    /** 마감 시각. 없으면 마감 없는 투표다. */
    @Embedded
    private ClosesAt closesAt;

    @OrderBy("position.value ASC")
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "poll_id", nullable = false)
    private List<PollOption> options = new ArrayList<>();

    /**
     * 투표를 만든다. 상태는 PUBLIC이고 공개 ID를 새로 발급한다. 선택지 순서(position)는 받은 순서대로 1부터 매긴다.
     * 공개 ID가 겹치면 저장 시점에 호출한 쪽이 다시 만든다.
     */
    public static Poll create(
        @NonNull Long authorId,
        String question,
        Category category,
        List<String> options,
        LocalDateTime closesAt,
        @NonNull LocalDateTime now
    ) {
        if (category == null) {
            throw AuddumException.validation("category", "카테고리는 필수입니다.");
        }

        Poll poll = new Poll();
        poll.publicId = PublicId.generate();
        poll.authorId = authorId;
        poll.question = PollQuestion.of(question);
        poll.category = category;
        poll.status = PollStatus.PUBLIC;
        poll.closesAt = closesAt == null ? null : ClosesAt.of(closesAt, now);
        poll.options = createOptions(options);

        return poll;
    }

    private static List<PollOption> createOptions(List<String> rawContents) {
        if (rawContents == null || rawContents.size() < MIN_OPTIONS || rawContents.size() > MAX_OPTIONS) {
            throw AuddumException.validation("options", "선택지는 2~5개여야 합니다.");
        }

        List<OptionContent> contents = rawContents.stream()
            .map(OptionContent::of)
            .toList();

        long distinctCount = contents.stream()
            .map(OptionContent::comparisonKey)
            .distinct()
            .count();
        if (distinctCount != contents.size()) {
            throw AuddumException.validation("options", "선택지는 서로 달라야 합니다.");
        }

        List<PollOption> options = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            options.add(new PollOption(Position.of(i + 1), contents.get(i)));
        }

        return options;
    }

    public List<PollOption> getOptions() {
        return Collections.unmodifiableList(options);
    }

    /** 서버 시각이 마감 시각 이상이면 마감이다. 마감 시각이 없으면 마감되지 않는다. */
    public boolean isClosed(LocalDateTime now) {
        return closesAt != null && closesAt.isReachedAt(now);
    }

    public boolean isPublic() {
        return status == PollStatus.PUBLIC;
    }

    public boolean isAuthor(Long memberId) {
        return authorId.equals(memberId);
    }

    /** 참여 가능 상태(공개이면서 마감 전)를 보장한다. 공개가 아니면 404, 마감되었으면 409다. */
    public void ensureVotable(LocalDateTime now) {
        if (!isPublic()) {
            throw new AuddumException(PollErrorCode.POLL_NOT_FOUND);
        }
        if (isClosed(now)) {
            throw new AuddumException(PollErrorCode.POLL_CLOSED);
        }
    }

    /** 이 투표의 선택지인지 보장하고 그 선택지를 돌려준다. 없으면 400이다. */
    public PollOption ensureHasOption(int position) {
        return options.stream()
            .filter(option -> option.isAt(position))
            .findFirst()
            .orElseThrow(() -> new AuddumException(PollErrorCode.INVALID_OPTION));
    }

    /** 관리자 숨김. 공개 투표만 숨길 수 있고, 이미 숨겨진 투표는 그대로 둔다. 삭제된 투표는 404다. */
    public void hide() {
        ensureNotDeleted();
        if (status == PollStatus.HIDDEN) {
            return;
        }

        this.status = PollStatus.HIDDEN;
    }

    /** 관리자 복구. 숨김 상태가 아니면 409다. 삭제된 투표는 복구할 수 없다. */
    public void restore() {
        if (status != PollStatus.HIDDEN) {
            throw new AuddumException(PollErrorCode.POLL_NOT_HIDDEN);
        }

        this.status = PollStatus.PUBLIC;
    }

    /**
     * 작성자 삭제. 작성자가 아니면 공개 투표는 403, 숨김·삭제 투표는 존재를 숨기려고 404다(P-01).
     * 작성자라도 삭제된 투표는 404, 관리자가 숨긴 투표는 403(POLL_NOT_DELETABLE)이다.
     */
    public void deleteByAuthor(Long memberId) {
        if (!isAuthor(memberId)) {
            throw new AuddumException(isPublic() ? DefaultErrorCode.FORBIDDEN : PollErrorCode.POLL_NOT_FOUND);
        }
        ensureNotDeleted();
        if (status == PollStatus.HIDDEN) {
            throw new AuddumException(PollErrorCode.POLL_NOT_DELETABLE);
        }

        this.status = PollStatus.DELETED;
    }

    /** 관리자 삭제. 공개·숨김 투표 모두 삭제할 수 있고, 이미 삭제된 투표는 404다. */
    public void deleteByAdmin() {
        ensureNotDeleted();

        this.status = PollStatus.DELETED;
    }

    private void ensureNotDeleted() {
        if (status == PollStatus.DELETED) {
            throw new AuddumException(PollErrorCode.POLL_NOT_FOUND);
        }
    }

}
