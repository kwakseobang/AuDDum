package org.kwakmunsu.auddum.admin.domain;

import org.kwakmunsu.auddum.poll.domain.Poll;

/** 관리자가 투표에 할 수 있는 조치(숨김·삭제). 요청 값이자 이력 종류와 투표 상태 변경을 잇는다. */
public enum PollTakedown {

    HIDDEN(ActionType.HIDE_POLL) {
        @Override
        public void apply(Poll poll) {
            poll.hide();
        }
    },
    DELETED(ActionType.DELETE_POLL) {
        @Override
        public void apply(Poll poll) {
            poll.deleteByAdmin();
        }
    };

    private final ActionType actionType;

    PollTakedown(ActionType actionType) {
        this.actionType = actionType;
    }

    public ActionType actionType() {
        return actionType;
    }

    /** 투표에 조치를 적용한다. 투표 상태가 맞지 않으면 도메인 오류(409·404)다. */
    public abstract void apply(Poll poll);

}
