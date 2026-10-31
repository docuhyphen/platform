/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    FieldOption,
    FieldValueType,
    HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactOfferDto,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
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
        validFrom: Date.parse("2026-09-01T08:00:00Z"),
        validTo: Date.parse("2027-09-01T08:00:00Z"),
        freshness: InformationRequestAcceptedFactFreshness.CURRENT,
    },
};

const renderOffer = (
    recertify = vi.fn(),
    overrides: { busy?: boolean; offer?: InformationRequestAcceptedFactOfferDto; options?: FieldOption[] } = {},
) =>
{
    const handlers = {onApplied: vi.fn(), onResult: vi.fn(), onCommandStart: vi.fn(), onCommandFailure: vi.fn()};
    render(
        <ReusableAnswerOffer elementId={"root-recorded-note"}
                             requestId={"request-a"}
                             requirementId={"requirement-a"}
                             options={overrides.options ?? []}
                             offer={overrides.offer ?? offer}
                             responseETag={"\"responses-4\""}
                             busy={overrides.busy ?? false}
                             recertify={recertify}
                             {...handlers}/>,
    );
    return handlers;
};

const duplicatedIds = (prefix: string): string[] => {
    const ids = Array.from(document.querySelectorAll("[id]"))
        .map(element => element.id)
        .filter(id => id.startsWith(prefix));
    return ids.filter((id, index) => ids.indexOf(id) !== index);
};

describe("ReusableAnswerOffer", () =>
{
    afterEach(cleanup);

    it("asks before clearing hidden answers and retries reuse with exactly the confirmed targets", async () => {
        const recertify = vi.fn().mockRejectedValueOnce({
            reasonCode: HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED,
            hiddenResponseClears: [{
                requirementId: "hidden-a",
                sourceTemplateBindingId: "binding-a",
                occurrencePath: "entries[0]"
            }],
        }).mockResolvedValue({outcome: "SAVED", responseETag: "\"responses-5\""});
        const handlers = renderOffer(recertify);
        fireEvent.click(screen.getByRole("checkbox", {name: "I confirm this answer is still accurate"}));
        fireEvent.click(screen.getByRole("button", {name: "Use this answer"}));
        const clear = await screen.findByRole("checkbox", {name: "Clear the answer at entries[0]"});
        expect((screen.getByRole("button", {name: "Use this answer"}) as HTMLButtonElement).disabled).toBe(true);
        expect(handlers.onApplied).not.toHaveBeenCalled();
        fireEvent.click(clear);
        fireEvent.click(screen.getByRole("button", {name: "Use this answer"}));
        await waitFor(() => expect(recertify).toHaveBeenLastCalledWith("request-a", "fact-a", "requirement-a", "\"responses-4\"", ["hidden-a"]));
        await waitFor(() => expect(handlers.onApplied).toHaveBeenCalledOnce());
    });

    it("keeps every id of one offer apart from an offer whose element key adds a suffix to it", () => {
        const keys = ["root-recorded-note", "root-recorded-note-value", "root-recorded-note-standing", "root-recorded-note-use"];
        render(
            <>
                {keys.map(key => (
                    <ReusableAnswerOffer key={key}
                                         elementId={key}
                                         requestId={"request-a"}
                                         requirementId={`requirement-${key}`}
                                         offer={offer}
                                         responseETag={"\"responses-4\""}
                                         busy={false}
                                         recertify={vi.fn()}
                                         onApplied={vi.fn()}
                                         onResult={vi.fn()}
                                         onCommandStart={vi.fn()}
                                         onCommandFailure={vi.fn()}/>
                ))}
            </>,
        );

        expect(screen.getAllByText("Recorded answer")).toHaveLength(keys.length);
        expect(duplicatedIds("information-request-reusable-answer-")).toEqual([]);
    });

    it("shows the earlier answer, how it was accepted, and its validity without source details", () =>
    {
        renderOffer();

        expect(screen.getByText("Recorded answer")).toBeTruthy();
        expect(screen.getByText(/Accepted by a reviewer/)).toBeTruthy();
        expect(screen.getByText(/Valid until/)).toBeTruthy();
        expect(document.body.textContent).not.toContain("policy.reuse");
    });

    it("shows a reused choice by its option labels and a reused time in the viewer's time zone", () => {
        const value = () => document.getElementById("information-request-reusable-answer-root-recorded-note-OFFER-value")?.textContent;
        const options: FieldOption[] = [{code: "option-a", label: "First choice"}, {
            code: "option-b",
            label: "Second choice"
        }];
        const fact = (valueType: FieldValueType, stated: unknown) => ({
            ...offer,
            fact: {...offer.fact, valueType, value: stated}
        });

        renderOffer(vi.fn(), {offer: fact(FieldValueType.MULTI_SELECT, ["option-a", "option-b"]), options});
        expect(value()).toBe("First choice, Second choice");
        cleanup();

        renderOffer(vi.fn(), {offer: fact(FieldValueType.SINGLE_SELECT, "option-b"), options});
        expect(value()).toBe("Second choice");
        cleanup();

        const recorded = "2026-09-01T08:30:00Z";
        renderOffer(vi.fn(), {offer: fact(FieldValueType.DATE_TIME, recorded)});
        expect(value()).toBe(formatInformationRequestTime(Date.parse(recorded)));
    });

    it("states the earlier of the validity end and the reuse cutoff the server sends as epoch milliseconds", () => {
        const cutoff = Date.parse("2027-03-01T08:00:00Z");
        const standing = () => document.getElementById("information-request-reusable-answer-root-recorded-note-OFFER-standing")?.textContent;
        renderOffer(vi.fn(), {offer: {...offer, fact: {...offer.fact, expiresAt: cutoff}}});

        expect(standing()).toBe(`Accepted by a reviewer. Valid until ${formatInformationRequestTime(cutoff)}`);
        cleanup();

        renderOffer(vi.fn(), {offer: {...offer, fact: {...offer.fact, validTo: undefined}}});

        expect(standing()).toBe("Accepted by a reviewer. No end date");
    });

    it("reads an end the server sends as null as no end, while epoch zero is still an end", () => {
        const cutoff = Date.parse("2027-03-01T08:00:00Z");
        const standing = () => document.getElementById("information-request-reusable-answer-root-recorded-note-OFFER-standing")?.textContent;
        renderOffer(vi.fn(), {offer: {...offer, fact: {...offer.fact, validTo: null, expiresAt: null}}});

        expect(standing()).toBe("Accepted by a reviewer. No end date");
        cleanup();

        renderOffer(vi.fn(), {offer: {...offer, fact: {...offer.fact, validTo: null, expiresAt: cutoff}}});

        expect(standing()).toBe(`Accepted by a reviewer. Valid until ${formatInformationRequestTime(cutoff)}`);
        cleanup();

        renderOffer(vi.fn(), {offer: {...offer, fact: {...offer.fact, validTo: 0, expiresAt: null}}});

        expect(standing()).toBe(`Accepted by a reviewer. Valid until ${formatInformationRequestTime(0)}`);
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
