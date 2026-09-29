import {BadgeProps} from "@fluentui/react-components";
import {
    InformationRequestClockEventKind,
    InformationRequestClockState,
    InformationRequestNoticeDeliveryState,
    InformationRequestNoticeKind,
    InformationRequestOperationsException,
    InformationRequestSlaStatus,
    InformationRequestState,
} from "../../models/models.tsx";
import {formatInformationRequestTime} from "../shared/informationRequestFormatting.ts";

type BadgeColor = NonNullable<BadgeProps["color"]>;

export const slaStatusPresentation: Record<InformationRequestSlaStatus, {label: string; color: BadgeColor}> = {
    [InformationRequestSlaStatus.NO_CLOCK]: {label: "No clock", color: "subtle"},
    [InformationRequestSlaStatus.ON_TRACK]: {label: "On track", color: "success"},
    [InformationRequestSlaStatus.DUE_SOON]: {label: "Due soon", color: "warning"},
    [InformationRequestSlaStatus.OVERDUE]: {label: "Overdue", color: "danger"},
    [InformationRequestSlaStatus.PAUSED]: {label: "Paused", color: "informative"},
    [InformationRequestSlaStatus.MET]: {label: "Met", color: "brand"},
};

export const requestStateLabels: Record<InformationRequestState, string> = {
    [InformationRequestState.DRAFT]: "Draft",
    [InformationRequestState.ISSUED]: "Issued",
    [InformationRequestState.IN_PROGRESS]: "In progress",
    [InformationRequestState.CLOSED]: "Closed",
    [InformationRequestState.CANCELLED]: "Cancelled",
    [InformationRequestState.SUPERSEDED]: "Superseded",
    [InformationRequestState.EXPIRED]: "Expired",
};

export const exceptionLabels: Record<InformationRequestOperationsException, string> = {
    [InformationRequestOperationsException.NOTICE_UNDELIVERABLE]: "undeliverable notices",
    [InformationRequestOperationsException.NOTICE_FAILED]: "failed notices",
    [InformationRequestOperationsException.CLOCK_ESCALATED]: "escalated clocks",
    [InformationRequestOperationsException.AUTOMATION_SKIPPED]: "skipped automations",
    [InformationRequestOperationsException.EVENT_DELIVERY_FAILING]: "failing event deliveries",
};

export const clockStateLabels: Record<InformationRequestClockState, string> = {
    [InformationRequestClockState.RUNNING]: "Running",
    [InformationRequestClockState.PAUSED]: "Paused",
    [InformationRequestClockState.STOPPED]: "Stopped",
};

export const clockEventLabels: Record<InformationRequestClockEventKind, string> = {
    [InformationRequestClockEventKind.STARTED]: "Started",
    [InformationRequestClockEventKind.PAUSED]: "Paused",
    [InformationRequestClockEventKind.RESUMED]: "Resumed",
    [InformationRequestClockEventKind.EXTENDED]: "Extended",
    [InformationRequestClockEventKind.REMINDED]: "Reminder due",
    [InformationRequestClockEventKind.OVERDUE]: "Overdue",
    [InformationRequestClockEventKind.ESCALATED]: "Escalated",
    [InformationRequestClockEventKind.EXPIRED]: "Expired",
    [InformationRequestClockEventKind.STOPPED]: "Stopped",
};

export const noticeCountLabels: Record<InformationRequestNoticeDeliveryState, string> = {
    [InformationRequestNoticeDeliveryState.PENDING]: "pending",
    [InformationRequestNoticeDeliveryState.CLAIMED]: "being prepared",
    [InformationRequestNoticeDeliveryState.RENDERED]: "ready to send",
    [InformationRequestNoticeDeliveryState.RETRYING]: "retrying",
    [InformationRequestNoticeDeliveryState.DELIVERED]: "delivered",
    [InformationRequestNoticeDeliveryState.FAILED]: "failed",
    [InformationRequestNoticeDeliveryState.UNDELIVERABLE]: "undeliverable",
};

export const noticeStateColors: Record<InformationRequestNoticeDeliveryState, BadgeColor> = {
    [InformationRequestNoticeDeliveryState.PENDING]: "informative",
    [InformationRequestNoticeDeliveryState.CLAIMED]: "informative",
    [InformationRequestNoticeDeliveryState.RENDERED]: "informative",
    [InformationRequestNoticeDeliveryState.RETRYING]: "warning",
    [InformationRequestNoticeDeliveryState.DELIVERED]: "success",
    [InformationRequestNoticeDeliveryState.FAILED]: "danger",
    [InformationRequestNoticeDeliveryState.UNDELIVERABLE]: "danger",
};

export const noticeKindLabels: Record<InformationRequestNoticeKind, string> = {
    [InformationRequestNoticeKind.REQUIREMENTS_AMENDED]: "Requirements amended",
    [InformationRequestNoticeKind.RESPONSE_REMINDER]: "Response reminder",
    [InformationRequestNoticeKind.RESPONSE_OVERDUE]: "Response overdue",
};

export const formattedTime = (value?: string): string => value ? formatInformationRequestTime(value) : "Not set";

export const formattedDuration = (totalSeconds: number): string =>
{
    const seconds = Math.max(0, Math.floor(totalSeconds));
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (days > 0) return `${days}d ${hours}h`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
};

export const shortId = (id: string): string => id.slice(0, 8);
