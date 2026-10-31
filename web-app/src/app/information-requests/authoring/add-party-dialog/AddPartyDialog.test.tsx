/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestOwnerType,
    InformationRequestShareRoleKey,
    InformationRequestSubjectKind,
} from "../../../models/models.tsx";
import AddPartyDialog from "./AddPartyDialog.tsx";

const registry = vi.hoisted(() => ({
    available: true,
    otherWorkspace: false,
    error: null as string | null,
    reload: () => Promise.resolve()
}));
const trustedRead = vi.hoisted(() => ({error: null as string | null, reload: () => Promise.resolve()}));

vi.mock("./usePartyCandidates.ts", () => ({
    usePartyCandidates: () => ({
        groups: [{id: "group-a", name: "Records team"}],
        people: [{id: "user-b", name: "Second Member (second@process.test)"}],
        subjects: registry.available
            ? [{
                id: "subject-a",
                subjectKind: InformationRequestSubjectKind.RECORD,
                references: [{authority: "Records office", identifierType: "Account", identifierValue: "A-100"}],
                createdAt: Date.parse("2026-09-27T08:00:00Z"),
            }]
            : [],
        subjectRegistryAvailable: registry.available,
        otherWorkspace: registry.otherWorkspace,
        error: registry.error,
        reload: registry.reload,
    }),
}));

vi.mock("./useTrustedRecipientCandidates.ts", () => ({
    useTrustedRecipientCandidates: (requestId: string) => ({
        candidates: requestId === "request-a" && !trustedRead.error
            ? [
                {
                    exchangeRecipientId: "recipient-a",
                    principalKind: "USER",
                    principalId: "user-t",
                    name: "Verified Member (Partner organization)",
                    trustSuspended: false
                },
                {
                    exchangeRecipientId: "recipient-b",
                    principalKind: "PRINCIPAL_GROUP",
                    principalId: "group-t",
                    name: "Published Group (Partner organization)",
                    trustSuspended: false
                },
                {
                    exchangeRecipientId: "recipient-c",
                    principalKind: "USER",
                    principalId: "user-s",
                    name: "Paused Member (Paused organization)",
                    trustSuspended: true
                },
            ]
            : [],
        error: trustedRead.error,
        reload: trustedRead.reload,
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
    render(<AddPartyDialog requestId={"request-a"}
                           owner={{ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "org-a"}}
                           busy={false}
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

    beforeEach(() => {
        vi.clearAllMocks();
        registry.available = true;
        registry.otherWorkspace = false;
        registry.error = null;
        trustedRead.error = null;
    });

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

    it("adds a person with an account chosen from the people available, by their account", () => {
        const {onAssign} = renderDialog();

        choose("Holder", "A person with an account");
        expect(screen.queryByLabelText("Email address")).toBeNull();
        expect(screen.getByText("Choose a person.")).toBeTruthy();
        expect((screen.getByRole("button", {name: "Add"}) as HTMLButtonElement).disabled).toBe(true);
        choose("Person", "Second Member (second@process.test)");
        fireEvent.click(screen.getByRole("button", {name: "Add"}));

        expect(onAssign).toHaveBeenCalledWith({roleKey: InformationRequestShareRoleKey.CONTRIBUTOR, userId: "user-b"});
    });

    it("adds a trusted recipient of the Exchange by its account or group and names the Exchange recipient it holds", () => {
        const {onAssign} = renderDialog();

        choose("Holder", "A trusted recipient");
        expect(screen.getByText("Choose a trusted recipient.")).toBeTruthy();
        const options = Array.from((screen.getByLabelText("Trusted recipient") as HTMLSelectElement).options).map(option => option.textContent);
        expect(options).not.toContain("Paused Member (Paused organization)");
        choose("Trusted recipient", "Verified Member (Partner organization)");
        fireEvent.click(screen.getByRole("button", {name: "Add"}));
        expect(onAssign).toHaveBeenCalledWith({
            roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
            userId: "user-t",
            exchangeRecipientId: "recipient-a",
        });

        cleanup();
        const second = renderDialog();
        choose("Holder", "A trusted recipient");
        choose("Trusted recipient", "Published Group (Partner organization)");
        fireEvent.click(screen.getByRole("button", {name: "Add"}));
        expect(second.onAssign).toHaveBeenCalledWith({
            roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
            principalGroupId: "group-t",
            exchangeRecipientId: "recipient-b",
        });
    });

    it("offers a reviewer only as a person with an account or a group, never by email", () => {
        const {onAssign} = renderDialog();

        choose("Role", "Reviewer");

        const holders = Array.from((screen.getByLabelText("Holder") as HTMLSelectElement).options).map(option => option.textContent);
        expect(holders).not.toContain("A person by email");
        expect(screen.queryByLabelText("Email address")).toBeNull();
        choose("Person", "Second Member (second@process.test)");
        fireEvent.click(screen.getByRole("button", {name: "Add"}));
        expect(onAssign).toHaveBeenCalledWith({roleKey: InformationRequestShareRoleKey.REVIEWER, userId: "user-b"});
    });

    it("offers only an email address or a trusted recipient for a request of another workspace and says why", () => {
        registry.otherWorkspace = true;
        renderDialog();

        const holders = Array.from((screen.getByLabelText("Holder") as HTMLSelectElement).options).map(option => option.textContent);
        expect(holders).toEqual(["A person by email", "A trusted recipient"]);
        expect(screen.getByText(/belongs to another workspace/)).toBeTruthy();
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

    it("names a new subject by its kind only for a manager without privacy management", () => {
        registry.available = false;
        const {onAssignSubject} = renderDialog();

        choose("Role", "Subject");
        expect(screen.queryByLabelText("Known subject")).toBeNull();
        expect(screen.queryByLabelText("Authority")).toBeNull();
        expect(screen.queryByLabelText("Identifier value")).toBeNull();
        expect(screen.getByText(
            "Naming a subject by a reference needs privacy management, such as an Organization Owner or Administrator.",
        )).toBeTruthy();
        choose("Subject kind", "Record");
        fireEvent.click(screen.getByRole("button", {name: "Add"}));

        expect(onAssignSubject).toHaveBeenCalledWith({subjectKind: InformationRequestSubjectKind.RECORD});
    });

    it("states a refused read of who can be named and reads it again on Try again", () => {
        registry.error = "The people who can be named could not be read.";
        registry.reload = vi.fn(() => Promise.resolve());
        renderDialog();

        const failure = document.getElementById("information-request-add-party-candidates-load-error") as HTMLElement;
        expect(failure.textContent).toContain("The people who can be named could not be read.");
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(registry.reload).toHaveBeenCalledTimes(1);
    });

    it("states a refused read of the trusted recipients, offers no trusted holder meanwhile, and reads it again on Try again", () => {
        trustedRead.error = "The trusted recipients who can be named could not be loaded.";
        trustedRead.reload = vi.fn(() => Promise.resolve());
        renderDialog();

        const failure = document.getElementById("information-request-add-party-trusted-load-error") as HTMLElement;
        expect(failure.textContent).toContain("The trusted recipients who can be named could not be loaded.");
        const holders = Array.from((screen.getByLabelText("Holder") as HTMLSelectElement).options).map(option => option.textContent);
        expect(holders).not.toContain("A trusted recipient");
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(trustedRead.reload).toHaveBeenCalledTimes(1);
    });
});

