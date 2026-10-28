package org.kwakmunsu.auddum.admin.domain;

/** 관리자 조치 종류(도메인-모델 §7). 이력에 남는 값이다. */
public enum ActionType {

    HIDE_POLL,
    DELETE_POLL,
    RESTORE_POLL,
    SUSPEND_MEMBER,
    UNSUSPEND_MEMBER,
    DISMISS_REPORT

}
