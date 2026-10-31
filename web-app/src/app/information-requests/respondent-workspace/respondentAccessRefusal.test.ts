import {describe, expect, it} from "vitest";
import {RECIPIENT_SIGN_IN_REQUIRED_GUIDANCE, respondentAccessRefusalMessage} from "./respondentAccessRefusal.ts";

describe("respondentAccessRefusalMessage", () => {
    it("tells a respondent what to do when the Exchange now requires sign-in or the link moved to an account", () => {
        expect(respondentAccessRefusalMessage({
            errorMessage: "This Exchange requires recipients to sign in; access links and their sessions no longer work",
            reasonCode: "INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED",
        })).toBe(RECIPIENT_SIGN_IN_REQUIRED_GUIDANCE);
        expect(respondentAccessRefusalMessage({
            errorMessage: "This access link belongs to a participant who now signs in with a linked account",
            reasonCode: "INFORMATION_REQUEST_ACCESS_LINK_PARTY_ACCOUNT_LINKED",
        })).toBe("Your part of this request is now linked to your account. Sign in to that account to open the request.");
    });

    it("leaves other refusals to the message the server sent", () => {
        expect(respondentAccessRefusalMessage({
            errorMessage: "This access link has been revoked",
            reasonCode: "INFORMATION_REQUEST_ACCESS_LINK_REVOKED",
        })).toBeUndefined();
        expect(respondentAccessRefusalMessage(new Error("Network unavailable"))).toBeUndefined();
    });
});
