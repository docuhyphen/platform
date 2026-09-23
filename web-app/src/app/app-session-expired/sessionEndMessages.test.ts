import {describe, expect, it} from "vitest";
import {getSessionEndMessage} from "./sessionEndMessages.ts";

describe("getSessionEndMessage", () =>
{
    it("describes inactivity timeout sign-outs", () =>
    {
        expect(getSessionEndMessage("INACTIVITY_TIMEOUT"))
            .toBe("You were signed out because your session was inactive.");
    });

    it("describes absolute session expiry", () =>
    {
        expect(getSessionEndMessage("session_expired"))
            .toBe("You were signed out because your session reached its maximum duration.");
    });

    it("uses the generic message for unknown reasons", () =>
    {
        expect(getSessionEndMessage("UNKNOWN"))
            .toBe("Your session has expired. Please sign in again.");
    });
});
