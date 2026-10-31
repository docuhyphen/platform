import {informationRequestRefusalCode} from "../shared/informationRequestRefusal.ts";

export const RECIPIENT_SIGN_IN_REQUIRED_GUIDANCE =
    "This Exchange now requires recipients to sign in, so access links no longer open its requests. Ask the person " +
    "who sent you this request to add you to the Exchange and assign your part of the request to your account.";

const accessRefusalMessages = new Map<string, string>([
    ["INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED", RECIPIENT_SIGN_IN_REQUIRED_GUIDANCE],
    [
        "INFORMATION_REQUEST_ACCESS_LINK_PARTY_ACCOUNT_LINKED",
        "Your part of this request is now linked to your account. Sign in to that account to open the request.",
    ],
]);

export const respondentAccessRefusalMessage = (error: unknown): string | undefined =>
    accessRefusalMessages.get(informationRequestRefusalCode(error) ?? "");
