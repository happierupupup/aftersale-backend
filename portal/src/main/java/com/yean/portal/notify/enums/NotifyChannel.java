package com.yean.portal.notify.enums;

/**
 * 通知渠道。电商售后以站内信为主；短信/邮件只落库并打日志，不接真实运营商。
 */
public enum NotifyChannel {
    INBOX,
    SMS,
    MAIL
}
