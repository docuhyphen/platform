import {describe, expect, it} from "vitest";
import {NotificationDto} from "../../app/models/models.tsx";
import {getNotificationTarget} from "../notificationNavigation.ts";

const notification = (type: string): NotificationDto => ({
    id: "notification-1",
    type,
    message: "Trial request update",
    timestamp: "2026-08-16T12:00:00Z",
    isRead: false,
    data: {trialRequestId: "request-1"},
});

describe("trial request notification navigation", () =>
{
    it("opens Trial Requests for App Administrators", () =>
    {
        expect(getNotificationTarget(notification("subscription_trial_request.created")))
            .toBe("/platform/administration?section=trial-requests");
    });

    it("opens Billing for requester decisions", () =>
    {
        expect(getNotificationTarget(notification("subscription_trial_request.approved")))
            .toBe("/settings?tab=OrganizationBillingTab");
        expect(getNotificationTarget(notification("subscription_trial_request.rejected")))
            .toBe("/settings?tab=OrganizationBillingTab");
    });
});
