import {
    informationRequestRefusalCode,
    informationRequestRefusalMessage,
    isInformationRequestAccessSessionEnded,
} from "../../shared/informationRequestRefusal.ts";
import {RECIPIENT_SIGN_IN_REQUIRED_GUIDANCE} from "../respondentAccessRefusal.ts";

const refusalMessages = new Map<string, string>([
    [
        "INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_EMAIL_MISMATCH",
        "Your account's email address is not the one this request was sent to. Sign in with the account for that address.",
    ],
    [
        "INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_ALREADY_LINKED",
        "This contact address is already linked to a different account.",
    ],
    [
        "INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_ACCESS_CONFLICT",
        "Your account already has its own access to this request. Open the request from your account instead.",
    ],
    [
        "INFORMATION_REQUEST_TRUST_SUSPENDED",
        "Your account cannot be linked while the trusted relationship behind this request is suspended.",
    ],
    [
        "INFORMATION_REQUEST_CONTACT_PROOF_REQUIRED",
        "Verify your contact address again, then link your account.",
    ],
    [
        "INFORMATION_REQUEST_PARTY_SHARING_NOT_PERMITTED",
        "The request's owner does not permit sharing it with this account. Ask the person who sent you this request how to continue.",
    ],
    ["INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED", RECIPIENT_SIGN_IN_REQUIRED_GUIDANCE],
    [
        "SUBSCRIPTION_SUSPENDED",
        "Changes to this request are paused, so your account cannot be linked yet. Try again later.",
    ],
    [
        "COMMAND_PRECONDITION_STALE",
        "This request changed while your account was being linked. Try again.",
    ],
]);

export const NO_LINKABLE_PARTY = "This access link has no party that can be linked to an account.";

export const accountLinkRefusalMessage = (error: unknown): string => {
    const code = informationRequestRefusalCode(error) ?? "";
    const known = refusalMessages.get(code);
    if (known) return known;
    if (isInformationRequestAccessSessionEnded(error)) return "Your verified session has ended. Verify your access again, then link your account.";
    return informationRequestRefusalMessage(error, "Your account could not be linked. Try again.");
};
