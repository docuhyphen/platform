import {describe, expect, it} from "vitest";
import {InformationRequestStandingReason} from "../../models/models.tsx";
import {standingReasonSentence} from "./executionStandingText.ts";

describe("standingReasonSentence", () => {
    it("explains the open-request limit", () => {
        expect(standingReasonSentence[InformationRequestStandingReason.OPEN_REQUEST_LIMIT_REACHED])
            .toBe("The owner has reached its plan's limit of open Information Requests.");
    });
});
