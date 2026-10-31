import {describe, expect, it} from "vitest";
import {commandErrorMessage} from "./responseSaveStatus.ts";

describe("commandErrorMessage", () => {
    it("names each refused item and counts the items the caller cannot see", () => {
        expect(commandErrorMessage({
            errorMessage: "Some answers could not be saved.",
            reasonCode: "INFORMATION_REQUEST_SUBMISSION_LOCKED",
            problems: [
                {
                    code: "SUBMISSION_LOCKED",
                    message: "This item is in a submitted part.",
                    requirementId: "requirement-a"
                },
                {
                    code: "SUBMISSION_LOCKED",
                    message: "This entry is in a submitted part.",
                    requirementId: "requirement-b"
                },
            ],
            undisclosedProblemCount: 2,
        })).toBe(
            "Some answers could not be saved. This item is in a submitted part. This entry is in a submitted part. "
            + "2 more items could not be shown.",
        );
    });

    it("keeps a plain refusal as it is stated", () => {
        expect(commandErrorMessage({
            errorMessage: "Refused",
            reasonCode: "INFORMATION_REQUEST_STATE_INVALID"
        })).toBe("Refused");
        expect(commandErrorMessage({errorMessage: "Refused", problems: [], undisclosedProblemCount: 1}))
            .toBe("Refused 1 more item could not be shown.");
    });
});
