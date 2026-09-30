import {describe, expect, it} from "vitest";
import {
    InformationRequestCapabilitiesDto,
    InformationRequestStandingReason,
    PlanCode,
    SubscriptionEnforcementMode,
    SubscriptionOwnerType,
    SubscriptionStatus,
} from "../../models/models.tsx";
import {scopeStandingNotice} from "./scopeStandingText.ts";

const capabilities = (
    ownerType: SubscriptionOwnerType,
    reason?: InformationRequestStandingReason,
    operationallySuspended = false,
): InformationRequestCapabilitiesDto => ({
    ownerType,
    planCode: ownerType === SubscriptionOwnerType.ORGANIZATION ? PlanCode.BUSINESS : PlanCode.FREE,
    subscriptionStatus: operationallySuspended ? SubscriptionStatus.SUSPENDED : SubscriptionStatus.ACTIVE,
    enforcementMode: SubscriptionEnforcementMode.ENFORCE,
    featureIncluded: reason !== InformationRequestStandingReason.FEATURE_NOT_INCLUDED,
    newWorkAvailable: reason === undefined,
    newWorkUnavailableReason: reason,
    operationallySuspended,
    typedAnswersAvailable: true,
    personalTemplatesAvailable: reason === undefined,
    assignedWork: false,
    holdsRequests: false,
});

describe("scopeStandingNotice", () =>
{
    it("says nothing while new work is available", () =>
    {
        expect(scopeStandingNotice(capabilities(SubscriptionOwnerType.USER))).toBeNull();
    });

    it("tells a person without the feature that shared requests stay open to them", () =>
    {
        expect(scopeStandingNotice(capabilities(SubscriptionOwnerType.USER, InformationRequestStandingReason.FEATURE_NOT_INCLUDED)))
            .toEqual({
                intent: "info",
                text: "Your plan does not include creating Information Requests. You can still respond to and review "
                    + "Information Requests shared with you, and everything recorded stays readable.",
            });
    });

    it("tells an organization after a lapse that issued requests continue", () =>
    {
        expect(scopeStandingNotice(capabilities(SubscriptionOwnerType.ORGANIZATION, InformationRequestStandingReason.TRIAL_ENDED)))
            .toEqual({
                intent: "warning",
                text: "This organization cannot create new Information Requests or Templates right now. The trial has ended. "
                    + "Requests already issued continue, and everything recorded stays readable.",
            });
    });

    it("tells a suspended owner that changes are paused but records stay readable", () =>
    {
        expect(scopeStandingNotice(capabilities(
            SubscriptionOwnerType.ORGANIZATION,
            InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
            true,
        ))).toEqual({
            intent: "warning",
            text: "This organization cannot create or change Information Requests while the account is suspended. "
                + "Everything recorded stays readable and exportable.",
        });
    });
});
