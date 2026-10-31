import {describe, expect, it} from "vitest";
import {
    COMPLETION_GATES_UNSATISFIED,
    refusalMessage,
    REMAINING_REQUESTS_NOT_CANCELLABLE,
    REMAINING_REQUESTS_REQUIRE_CANCELLATION,
} from "./exchangeEndRefusal.ts";

const refusal = (reasonCode: string, informationRequestIds: string[]) => ({
    errorMessage: "This Exchange cannot end while its Information Requests are open",
    reasonCode,
    informationRequestIds,
});

describe("refusalMessage", () => {
    it("counts only the requests the refusal names, in the singular for one", () => {
        expect(refusalMessage(refusal(REMAINING_REQUESTS_REQUIRE_CANCELLATION, ["request-a"])))
            .toBe("1 Information Request on this Exchange is still open. Ending the Exchange now cancels it.");
        expect(refusalMessage(refusal(COMPLETION_GATES_UNSATISFIED, ["request-a", "request-b"])))
            .toBe("This Exchange cannot end until 2 Information Requests that must finish first are complete.");
    });

    it("formats a large count in the viewer's number format", () => {
        const ids = Array.from({length: 1200}, (_, index) => `request-${index}`);
        const formatted = new Intl.NumberFormat().format(1200);

        expect(refusalMessage(refusal(REMAINING_REQUESTS_REQUIRE_CANCELLATION, ids)))
            .toBe(`${formatted} Information Requests on this Exchange are still open. Ending the Exchange now cancels them.`);
        expect(refusalMessage(refusal(COMPLETION_GATES_UNSATISFIED, ids)))
            .toBe(`This Exchange cannot end until ${formatted} Information Requests that must finish first are complete.`);
    });

    it("states no number when it names no request the caller may see", () => {
        expect(refusalMessage(refusal(REMAINING_REQUESTS_REQUIRE_CANCELLATION, [])))
            .toBe("Information Requests on this Exchange are still open. Ending the Exchange now cancels them.");
        expect(refusalMessage(refusal(COMPLETION_GATES_UNSATISFIED, [])))
            .toBe("This Exchange cannot end until Information Requests that must finish first are complete.");
    });

    it("explains that only the people who manage the open requests can cancel them", () => {
        expect(refusalMessage(refusal(REMAINING_REQUESTS_NOT_CANCELLABLE, [])))
            .toBe("Information Requests on this Exchange are still open, and only the people who manage them can cancel them.");
    });
});
