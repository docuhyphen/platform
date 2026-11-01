// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    changeInformationRequestClock,
    defineInformationRequestClockPolicy,
    getInformationRequestClockPolicies,
    getInformationRequestPrivacyRequests,
    getInformationRequestSubjectRestrictions,
    liftInformationRequestSubjectRestriction,
    publishInformationRequestClockPolicyVersion,
    recordInformationRequestPrivacyRequest,
    searchInformationRequestAuditEvents,
    sendInformationRequestReminders,
    startInformationRequestClock,
} from "../informationRequestAdministrationService.ts";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockType,
    InformationRequestClockUrgency,
    InformationRequestPrivacyRequestKind,
    InformationRequestWorkingPeriodDto,
} from "../../app/models/models.tsx";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

const definition = {
    clockType: InformationRequestClockType.CALENDAR,
    businessTimezone: "UTC",
    workingPeriods: [] as InformationRequestWorkingPeriodDto[],
    holidays: [] as string[],
    standardDurationMinutes: 2880,
    urgentDurationMinutes: 480,
    reminderMinutesBeforeDue: [1440],
    dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
};

describe("Information Request administration transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(apiClient.get).mockResolvedValue({data: []});
        vi.mocked(apiClient.post).mockResolvedValue({data: {}, headers: {}});
    });

    it("reads clock policies and defines them and publishes their versions under an Idempotency-Key", async () =>
    {
        await getInformationRequestClockPolicies();
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-clock-policies");
        await defineInformationRequestClockPolicy({
            policyKey: "standard",
            displayName: "Standard",
            definition
        }, "define-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-request-clock-policies",
            {policyKey: "standard", displayName: "Standard", definition},
            {headers: {"Idempotency-Key": "define-a"}},
        );
        await publishInformationRequestClockPolicyVersion("policy-a", definition, "publish-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-request-clock-policies/policy-a/versions",
            definition,
            {headers: {"Idempotency-Key": "publish-a"}},
        );
    });

    it("starts a clock under its Idempotency-Key and changes one under its clock ETag", async () =>
    {
        await startInformationRequestClock(
            "request-a",
            {clockKey: "response", policyVersionId: "version-a", urgency: InformationRequestClockUrgency.STANDARD},
            "start-a",
        );
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/clocks",
            {clockKey: "response", policyVersionId: "version-a", urgency: InformationRequestClockUrgency.STANDARD},
            {headers: {"Idempotency-Key": "start-a"}},
        );
        await changeInformationRequestClock("request-a", "clock-a", "extensions", {reasonCode: "MORE_TIME", extensionMinutes: 60}, "\"clock-3\"", "extend-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/clocks/clock-a/extensions",
            {reasonCode: "MORE_TIME", extensionMinutes: 60},
            {headers: {"Idempotency-Key": "extend-a", "If-Match": "\"clock-3\""}},
        );
    });

    it("sends reminders for named requests under one Idempotency-Key", async () =>
    {
        vi.mocked(apiClient.post).mockResolvedValue({data: [{requestId: "request-a", noticeCount: 2}], headers: {}});

        expect(await sendInformationRequestReminders(["request-a"], "remind-a")).toEqual([{requestId: "request-a", noticeCount: 2}]);
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-request-reminders",
            {requestIds: ["request-a"]},
            {headers: {"Idempotency-Key": "remind-a"}},
        );
    });

    it("records privacy requests, lists them and restrictions, lifts a restriction, and searches the audit trail", async () =>
    {
        await recordInformationRequestPrivacyRequest({
            subjectIdentityRefId: "subject-a",
            requestKind: InformationRequestPrivacyRequestKind.ACCESS,
            purposeKey: "subject-access",
            policyBasisKey: "consent",
        }, "privacy-key");
        expect(apiClient.post).toHaveBeenLastCalledWith("/information-request-privacy-requests", {
            subjectIdentityRefId: "subject-a",
            requestKind: InformationRequestPrivacyRequestKind.ACCESS,
            purposeKey: "subject-access",
            policyBasisKey: "consent",
        }, {headers: {"Idempotency-Key": "privacy-key"}});
        await getInformationRequestPrivacyRequests("subject-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-privacy-requests", {params: {subjectIdentityRefId: "subject-a"}});
        await getInformationRequestSubjectRestrictions();
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-subject-restrictions");
        await liftInformationRequestSubjectRestriction("restriction-a", "resolved", "lift-key");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-request-subject-restrictions/restriction-a/lift",
            {reasonCode: "resolved"},
            {headers: {"Idempotency-Key": "lift-key"}},
        );
        await searchInformationRequestAuditEvents({eventType: "information_request.request.remind", limit: 25, offset: 0});
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-audit-events", {
            params: {eventType: "information_request.request.remind", limit: 25, offset: 0},
        });
    });
});
