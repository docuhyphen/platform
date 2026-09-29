/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactOfferDto,
} from "../../../models/models.tsx";
import ReusableAnswerOffer from "./ReusableAnswerOffer.tsx";

const offer: InformationRequestAcceptedFactOfferDto = {
    requirementId: "requirement-a",
    requirementKey: "recorded-note",
    reconfirmationRequired: true,
    fact: {
        id: "fact-a",
        purposeKey: "profile.reuse",
        policyBasisKey: "policy.reuse",
        valueType: FieldValueType.SHORT_TEXT,
        value: "Recorded answer",
        confidence: InformationRequestAcceptedFactConfidence.REVIEWED,
        validFrom: "2026-09-01T08:00:00Z",
        validTo: "2027-09-01T08:00:00Z",
        freshness: InformationRequestAcceptedFactFreshness.CURRENT,
    },
};

const renderOffer = (recertify = vi.fn(), overrides: {busy?: boolean} = {}) =>
{
    const handlers = {onApplied: vi.fn(), onResult: vi.fn(), onCommandStart: vi.fn(), onCommandFailure: vi.fn()};
    render(
        <ReusableAnswerOffer elementId={"root-recorded-note"}
                             requestId={"request-a"}
                             requirementId={"requirement-a"}
                             offer={offer}
                             responseETag={"\"responses-4\""}
                             busy={overrides.busy ?? false}
                             recertify={recertify}
                             {...handlers}/>,
    );
    return handlers;
};

describe("ReusableAnswerOffer", () =>
{
    afterEach(cleanup);

    it("shows the earlier answer, how it was accepted, and its validity without source details", () =>
    {
        renderOffer();

        expect(screen.getByText("Recorded answer")).toBeTruthy();
        expect(screen.getByText(/Accepted by a reviewer/)).toBeTruthy();
        expect(screen.getByText(/Valid until/)).toBeTruthy();
        expect(document.body.textContent).not.toContain("policy.reuse");
    });

    it("uses the earlier answer only after the respondent confirms it is still accurate", async () =>
    {
        const recertify = vi.fn().mockResolvedValue({outcome: "SAVED", responseETag: "\"responses-5\""});
        const handlers = renderOffer(recertify);
        const use = screen.getByRole("button", {name: "Use this answer"}) as HTMLButtonElement;

        expect(use.disabled).toBe(true);
        fireEvent.click(screen.getByRole("checkbox", {name: "I confirm this answer is still accurate"}));
        expect(use.disabled).toBe(false);
        fireEvent.click(use);

        await waitFor(() => expect(handlers.onResult).toHaveBeenCalledWith({outcome: "SAVED", responseETag: "\"responses-5\""}));
        expect(handlers.onCommandStart).toHaveBeenCalled();
        expect(recertify).toHaveBeenCalledWith("request-a", "fact-a", "requirement-a", "\"responses-4\"");
        expect(handlers.onApplied).toHaveBeenCalled();
    });

    it("keeps unsaved edits when the answers changed elsewhere and reports a refusal", async () =>
    {
        const stale = vi.fn().mockResolvedValue({outcome: "STALE"});
        const staleHandlers = renderOffer(stale);
        fireEvent.click(screen.getByRole("checkbox", {name: "I confirm this answer is still accurate"}));
        fireEvent.click(screen.getByRole("button", {name: "Use this answer"}));
        await waitFor(() => expect(staleHandlers.onResult).toHaveBeenCalledWith({outcome: "STALE"}));
        expect(staleHandlers.onApplied).not.toHaveBeenCalled();
        cleanup();

        const refusal = {message: "This earlier answer is no longer available to reuse here"};
        const refused = vi.fn().mockRejectedValue(refusal);
        const refusedHandlers = renderOffer(refused);
        fireEvent.click(screen.getByRole("checkbox", {name: "I confirm this answer is still accurate"}));
        fireEvent.click(screen.getByRole("button", {name: "Use this answer"}));
        await waitFor(() => expect(refusedHandlers.onCommandFailure).toHaveBeenCalledWith(refusal));
        expect(refusedHandlers.onApplied).not.toHaveBeenCalled();
    });

    it("cannot be used while another response command is running", () =>
    {
        renderOffer(vi.fn(), {busy: true});
        fireEvent.click(screen.getByRole("checkbox", {name: "I confirm this answer is still accurate"}));

        expect((screen.getByRole("button", {name: "Use this answer"}) as HTMLButtonElement).disabled).toBe(true);
    });
});
