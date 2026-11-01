/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {InformationRequestPartyDto, InformationRequestShareRoleKey, InformationRequestSubjectDto} from "../../../models/models.tsx";
import {TrustedPartyCandidate} from "../party-holder-fields/partyHolder.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";
import ReassignPartyDialog from "./ReassignPartyDialog.tsx";

const trustedRead = vi.hoisted(() => ({error: null as string | null, reload: () => Promise.resolve()}));

vi.mock("../add-party-dialog/useTrustedRecipientCandidates.ts", () => ({
    useTrustedRecipientCandidates: () => ({candidates: [] as TrustedPartyCandidate[], error: trustedRead.error, reload: trustedRead.reload}),
}));
vi.mock("../add-party-dialog/usePartyCandidates.ts", () => ({
    usePartyCandidates: () => ({
        groups: [{id: "group-a", name: "Records team"}],
        people: [{id: "user-b", name: "Second Member (second@process.test)"}],
        subjects: [] as InformationRequestSubjectDto[],
    }),
}));

const party: InformationRequestPartyDto = {
    id: "party-a",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalId: "participant-a",
    principalKind: "PARTICIPANT",
    assignedAt: Date.parse("2026-09-20T08:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party-a-1\"",
    label: "Counterparty desk",
};

const choose = (label: string, option: string) => {
    const select = screen.getByLabelText(label) as HTMLSelectElement;
    const value = Array.from(select.options).find(candidate => candidate.textContent === option)?.value ?? "";
    fireEvent.change(select, {target: {value}});
};

const renderDialog = () => {
    const onReassign = vi.fn();
    render(<ReassignPartyDialog requestId={"request-a"}
                                owner={null}
                                party={party}
                                busy={false}
                                onReassign={onReassign}
                                onDismiss={vi.fn()}/>);
    return onReassign;
};

describe("ReassignPartyDialog", () => {
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

    beforeEach(() => {
        vi.clearAllMocks();
        trustedRead.error = null;
    });

    afterEach(cleanup);

    it("hands the role to a person with an account and says what the current holder loses", () => {
        const onReassign = renderDialog();

        expect(screen.getByRole("dialog", {name: "Reassign party"})).toBeTruthy();
        expect(screen.getByText(/takes over the Contributor role from Counterparty desk/)).toBeTruthy();
        const holders = Array.from((screen.getByLabelText("Holder") as HTMLSelectElement).options).map(option => option.textContent);
        expect(holders).toEqual(["A person with an account", "A group"]);
        expect((screen.getByRole("button", {name: "Reassign"}) as HTMLButtonElement).disabled).toBe(true);
        choose("Person", "Second Member (second@process.test)");
        fireEvent.click(screen.getByRole("button", {name: "Reassign"}));

        expect(onReassign).toHaveBeenCalledWith({userId: "user-b"});
    });

    it("hands the role to a group", () => {
        const onReassign = renderDialog();

        choose("Holder", "A group");
        choose("Group", "Records team");
        fireEvent.click(screen.getByRole("button", {name: "Reassign"}));

        expect(onReassign).toHaveBeenCalledWith({principalGroupId: "group-a"});
    });

    it("names every control", () => {
        renderDialog();

        expect(unnamedControls(document.body)).toEqual([]);

        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("states a refused read of the trusted recipients and reads it again on Try again", () => {
        trustedRead.error = "The trusted recipients who can be named could not be loaded.";
        trustedRead.reload = vi.fn(() => Promise.resolve());
        renderDialog();

        const failure = document.getElementById("information-request-reassign-party-trusted-load-error") as HTMLElement;
        expect(failure.textContent).toContain("The trusted recipients who can be named could not be loaded.");
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(trustedRead.reload).toHaveBeenCalledTimes(1);
    });
});
