import {
    InformationRequestCapabilitiesDto,
    InformationRequestStandingReason,
    SubscriptionOwnerType,
} from "../../models/models.tsx";
import {ExecutionStandingNoticeText} from "../shared/executionStandingText.ts";

const lapseSentence: Partial<Record<InformationRequestStandingReason, string>> = {
    [InformationRequestStandingReason.TRIAL_ENDED]: "The trial has ended.",
    [InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE]: "Payment for the subscription is overdue.",
    [InformationRequestStandingReason.SUBSCRIPTION_CANCELED]: "The subscription has been canceled.",
};

export const scopeStandingNotice = (capabilities: InformationRequestCapabilitiesDto): ExecutionStandingNoticeText | null =>
{
    if (capabilities.newWorkAvailable) return null;
    const organization = capabilities.ownerType === SubscriptionOwnerType.ORGANIZATION;
    const subject = organization ? "This organization" : "You";

    if (capabilities.operationallySuspended)
    {
        return {
            intent: "warning",
            text: `${subject} cannot create or change Information Requests while the account is suspended. `
                + "Everything recorded stays readable and exportable.",
        };
    }
    if (capabilities.newWorkUnavailableReason === InformationRequestStandingReason.FEATURE_NOT_INCLUDED)
    {
        return {
            intent: "info",
            text: `${organization ? "This organization's plan" : "Your plan"} does not include creating Information Requests. `
                + "You can still respond to and review Information Requests shared with you, and everything recorded stays readable.",
        };
    }
    const reason = capabilities.newWorkUnavailableReason ? lapseSentence[capabilities.newWorkUnavailableReason] : undefined;
    return {
        intent: "warning",
        text: [
            `${subject} cannot create new Information Requests or Templates right now.`,
            reason,
            "Requests already issued continue, and everything recorded stays readable.",
        ].filter(Boolean).join(" "),
    };
};
