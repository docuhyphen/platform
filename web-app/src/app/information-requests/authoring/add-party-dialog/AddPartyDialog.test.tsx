/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {InformationRequestShareRoleKey, InformationRequestSubjectKind} from "../../../models/models.tsx";
import AddPartyDialog from "./AddPartyDialog.tsx";

vi.mock("./usePartyCandidates.ts", () => ({
    usePartyCandidates: () => ({
        groups: [{id: "group-a", name: "Records team"}],
        subjects: [{
            id: "subject-a",
            subjectKind: InformationRequestSubjectKind.RECORD,
            references: [{authority: "Records office", identifierType: "Account", identifierValue: "A-100"}],
            createdAt: "2026-09-27T08:00:00Z",
        }],
    }),
}));

const choose = (label: string, option: string) =>
{
    const select = screen.getByLabelText(label) as HTMLSelectElement;
    const value = Array.from(select.options).find(candidate => candidate.textContent === option)?.value ?? "";
    fireEvent.change(select, {target: {value}});
};

const renderDialog = () =>
{
    const onAssign = vi.fn();
    const onAssignSubject = vi.fn();
    render(<AddPartyDialog busy={false}
                           onAssign={onAssign}
                           onAssignSubject={onAssignSubject}
                           onDismiss={vi.fn()}/>);
    return {onAssign, onAssignSubject};
};

describe("AddPartyDialog", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() => vi.clearAllMocks());

    afterEach(cleanup);

    it("adds a person by email in the chosen role once the address is valid", () =>
    {
        const {onAssign} = renderDialog();

        choose("Role", "Preparer");
        fireEvent.change(screen.getByLabelText("Email address"), {target: {value: "member"}});
        expect(screen.getByText("Enter an email address.")).toBeTruthy();
        expect((screen.getByRole("button", {name: "Add"}) as HTMLButtonElement).disabled).toBe(true);
        fireEvent.change(screen.getByLabelText("Email address"), {target: {value: " member@process.test "}});
        fireEvent.click(screen.getByRole("button", {name: "Add"}));

        expect(onAssign).toHaveBeenCalledWith({roleKey: InformationRequestShareRoleKey.PREPARER, email: "member@process.test"});
    });

    it("adds a group", () =>
    {
        const {onAssign} = renderDialog();

        choose("Holder", "A group");
        choose("Group", "Records team");
        fireEvent.click(screen.getByRole("button", {name: "Add"}));

        expect(onAssign).toHaveBeenCalledWith({roleKey: InformationRequestShareRoleKey.CONTRIBUTOR, principalGroupId: "group-a"});
    });

    it("names a known subject or a new one by its reference, never by a name or email", () =>
    {
        const {onAssign, onAssignSubject} = renderDialog();

        choose("Role", "Subject");
        expect(screen.queryByLabelText("Email address")).toBeNull();
        choose("Known subject", "Record: Account A-100");
        fireEvent.click(screen.getByRole("button", {name: "Add"}));
        expect(onAssign).toHaveBeenCalledWith({roleKey: InformationRequestShareRoleKey.SUBJECT, subjectIdentityRefId: "subject-a"});

        cleanup();
        const second = renderDialog();
        choose("Role", "Subject");
        choose("Subject kind", "Record");
        fireEvent.change(screen.getByLabelText("Authority"), {target: {value: "Records office"}});
        expect(screen.getByText("Give the reference's authority, type, and value, or none of them.")).toBeTruthy();
        fireEvent.change(screen.getByLabelText("Identifier type"), {target: {value: "Account"}});
        fireEvent.change(screen.getByLabelText("Identifier value"), {target: {value: "B-200"}});
        fireEvent.click(screen.getByRole("button", {name: "Add"}));

        expect(second.onAssignSubject).toHaveBeenCalledWith({
            subjectKind: InformationRequestSubjectKind.RECORD,
            reference: {authority: "Records office", identifierType: "Account", identifierValue: "B-200"},
        });
        expect(onAssignSubject).not.toHaveBeenCalled();
    });
});
