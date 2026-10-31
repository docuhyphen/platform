import {describe, expect, it} from "vitest";
import {submissionErrorMessage} from "./submissionLabels.ts";

describe("submissionErrorMessage", () => {
    it("explains a refusal by the Exchange's state instead of repeating the server's wording", () => {
        expect(submissionErrorMessage({
            errorMessage: "Information Request parent state does not permit this mutation",
            reasonCode: "INFORMATION_REQUEST_PARENT_STATE_INVALID",
        }, "This could not be completed.")).toBe(
            "The Exchange holding this request is not accepting submissions now, for example because it has not been " +
            "accepted yet or it has ended.",
        );
    });

    it("keeps the server's wording for other refusals and the fallback when there is none", () => {
        expect(submissionErrorMessage({errorMessage: "A later part was submitted", reasonCode: "X"}, "fallback"))
            .toBe("A later part was submitted");
        expect(submissionErrorMessage({}, "fallback")).toBe("fallback");
    });
});
