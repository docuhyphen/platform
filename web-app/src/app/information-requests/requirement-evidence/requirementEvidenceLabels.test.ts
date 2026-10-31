import {describe, expect, it} from "vitest";
import {findingMessage, formatEvidenceSize, refusalMessage} from "./requirementEvidenceLabels.ts";
import {InformationRequestEvidenceFindingCode} from "../../models/models.tsx";

describe("refusalMessage", () => {
    it("explains an upload over the size limit in the server's words", () => {
        const message = refusalMessage(
            {
                errorMessage: "An evidence file is at most 50 MB",
                reasonCode: "INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED",
            },
            "The file could not be uploaded.",
        );

        expect(message).toBe("An evidence file is at most 50 MB");
    });

    it("explains a refusal by the Exchange's state instead of repeating the server's wording", () => {
        expect(refusalMessage({
            reasonCode: "INFORMATION_REQUEST_PARENT_STATE_INVALID",
            errorMessage: "Information Request parent state does not permit this mutation",
        }, "Fallback")).toBe(
            "The Exchange holding this request is not accepting files now, for example because it has not been " +
            "accepted yet or it has ended.",
        );
    });

    it("states the limit the server names instead of calling every limit refusal a file that is too large", () => {
        expect(refusalMessage({
            reasonCode: "INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED",
            errorMessage: "Each respondent stores at most 100 evidence files on a request",
        }, "Fallback")).toBe("Each respondent stores at most 100 evidence files on a request");
        expect(refusalMessage({reasonCode: "INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED"}, "Fallback"))
            .toBe("This upload is over a file or storage limit for this request.");
    });

    it("states a policy refusal in the server's words and in readable words when the server gives none", () => {
        expect(refusalMessage({
            reasonCode: "INFORMATION_REQUEST_EVIDENCE_POLICY_REFUSED",
            errorMessage: "This Requirement accepts at most 2 files",
        }, "Fallback")).toBe("This Requirement accepts at most 2 files");
        expect(refusalMessage({reasonCode: "INFORMATION_REQUEST_EVIDENCE_POLICY_REFUSED"}, "Fallback"))
            .toBe("This file is not one this Requirement accepts.");
    });

    it("falls back to the stated message for a refusal without a known reason", () => {
        expect(refusalMessage({errorMessage: "Refused"}, "Fallback")).toBe("Refused");
        expect(refusalMessage(undefined, "Fallback")).toBe("Fallback");
    });
});

describe("formatEvidenceSize", () => {
    it("states no size for a length the server sends as null, and states a zero length", () => {
        expect(formatEvidenceSize(null)).toBe("");
        expect(formatEvidenceSize(undefined)).toBe("");
        expect(formatEvidenceSize(0)).toBe("0 bytes");
        expect(formatEvidenceSize(2048)).toBe("2 KiB");
    });
});

it("states a one-file minimum in the singular", () => {
    expect(findingMessage({
        code: InformationRequestEvidenceFindingCode.FILE_COUNT_BELOW_MINIMUM,
        blocking: true,
        detail: "1"
    }))
        .toBe("At least 1 accepted file is needed.");
});

it("states a one-file maximum in the singular and a larger maximum in the plural", () => {
    const maximum = (detail: string) =>
        findingMessage({code: InformationRequestEvidenceFindingCode.FILE_COUNT_ABOVE_MAXIMUM, blocking: true, detail});

    expect(maximum("1")).toBe("At most 1 file is accepted.");
    expect(maximum("3")).toBe("At most 3 files are accepted.");
});
