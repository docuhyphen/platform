/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestSubmissionService.ts";
import {
    FieldValueType,
    InformationRequestCarryForwardDecision,
    InformationRequestCarryForwardDto,
    InformationRequestResponseDisposition,
} from "../../../models/models.tsx";
import InformationRequestCarryForwardList from "./InformationRequestCarryForwardList.tsx";

vi.mock("../../../../services/informationRequestSubmissionService.ts", () => ({
    getInformationRequestCarryForwards: vi.fn(),
}));

const offered: InformationRequestCarryForwardDto = {
    requirementId: "requirement-a",
    decision: InformationRequestCarryForwardDecision.OFFERED,
    sourcePackageId: "package-a",
    priorDisposition: InformationRequestResponseDisposition.PROVIDED,
    priorFieldValue: "Twelve records",
};

describe("InformationRequestCarryForwardList", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    afterEach(cleanup);

    it("shows an earlier choice by its option label, a flag as Yes or No, and an exception by its answer and reason", async () => {
        vi.mocked(transport.getInformationRequestCarryForwards).mockResolvedValueOnce([
            {...offered, requirementId: "requirement-choice", priorFieldValue: "kept-on-site"},
            {...offered, requirementId: "requirement-flag", priorFieldValue: true},
            {
                requirementId: "requirement-reason",
                decision: InformationRequestCarryForwardDecision.OFFERED,
                sourcePackageId: "package-a",
                priorDisposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
                priorNarrative: "No records were kept",
            },
        ]);

        render(<InformationRequestCarryForwardList requestId={"request-a"}
                                                   requirementLabels={{
                                                       "requirement-choice": "Where are the records kept?",
                                                       "requirement-flag": "Were the records audited?",
                                                       "requirement-reason": "How many records were kept?",
                                                   }}
                                                   valueFormats={{
                                                       "requirement-choice": {
                                                           valueType: FieldValueType.SINGLE_SELECT,
                                                           options: [{code: "kept-on-site", label: "Kept on site"}],
                                                       },
                                                       "requirement-flag": {
                                                           valueType: FieldValueType.BOOLEAN,
                                                           options: []
                                                       },
                                                   }}/>);

        expect(await screen.findByText("Kept on site")).toBeTruthy();
        expect(screen.getByText("Yes")).toBeTruthy();
        expect(screen.getByText("Not applicable: No records were kept")).toBeTruthy();
        expect(screen.queryByText("kept-on-site")).toBeNull();
    });

    it("states a refused read of earlier answers with Try again and shows them once a retry loads", async () => {
        vi.mocked(transport.getInformationRequestCarryForwards)
            .mockRejectedValueOnce({errorMessage: "The earlier answers could not be read."})
            .mockResolvedValueOnce([offered]);

        render(<InformationRequestCarryForwardList requestId={"request-a"}
                                                   requirementLabels={{"requirement-a": "How many records were kept?"}}/>);

        await waitFor(() => expect(document.getElementById("information-request-carry-forwards-load-error")?.textContent)
            .toContain("The earlier answers could not be read."));
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(await screen.findByText("Twelve records")).toBeTruthy();
        expect(screen.getByRole("region", {name: "From your previous submission"})).toBeTruthy();
        expect(document.getElementById("information-request-carry-forwards-load-error")).toBeNull();
    });
});
