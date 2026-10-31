/** @vitest-environment jsdom */
import {cleanup, render, screen, within} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {InformationRequestContributorRole, InformationRequestRequirementType} from "../../models/models.tsx";
import {newRequirement} from "./templateDraftDocument.ts";
import RequirementAnsweringFields from "./requirement-answering-fields/RequirementAnsweringFields.tsx";
import RequirementConfirmationFields from "./requirement-confirmation-fields/RequirementConfirmationFields.tsx";

vi.mock("./TemplateDocumentContext.ts", () => ({
    useTemplateDocument: () => ({document: {groups: [], conditionRules: []}}),
}));

describe("Requirement role choices", () => {
    afterEach(cleanup);

    it.each([
        InformationRequestRequirementType.DOCUMENT,
        InformationRequestRequirementType.FIELD,
    ])("offers only the roles that can answer a %s requirement", requirementType => {
        render(<RequirementAnsweringFields id={"requirement-answering"}
                                           draft={newRequirement(requirementType, "supporting-record", "Provide the record")}
                                           readOnly={false}
                                           onChange={vi.fn()}
                                           onDraftReplaced={vi.fn()}/>);

        const options = within(screen.getByLabelText("Answered by")).getAllByRole("option");
        expect(options.map(option => option.textContent)).toEqual(["Contributor", "Preparer"]);
    });

    it("still offers Attestor to answer a response attestation", () => {
        render(<RequirementAnsweringFields id={"attestation-answering"}
                                           draft={newRequirement(InformationRequestRequirementType.RESPONSE_ATTESTATION, "recorded-assertion", "Confirm the records")}
                                           readOnly={false}
                                           onChange={vi.fn()}
                                           onDraftReplaced={vi.fn()}/>);

        const options = within(screen.getByLabelText("Answered by")).getAllByRole("option");
        expect(options.map(option => option.textContent)).toEqual(["Contributor", "Preparer", "Attestor"]);
    });

    it("asks for an answering role when a stored document draft still names Attestor", () => {
        render(<RequirementAnsweringFields id={"stored-attestor-answering"}
                                           draft={{
                                               ...newRequirement(InformationRequestRequirementType.DOCUMENT, "supporting-record", "Provide the record"),
                                               contributorRole: InformationRequestContributorRole.ATTESTOR,
                                           }}
                                           readOnly={false}
                                           onChange={vi.fn()}
                                           onDraftReplaced={vi.fn()}/>);

        expect((screen.getByLabelText("Answered by") as HTMLSelectElement).value).toBe("");
        expect(screen.getByRole("option", {name: "Choose an acting role"})).toBeTruthy();
    });

    it("offers acting roles for confirmations", () => {
        render(<RequirementConfirmationFields id={"requirement-confirmation"}
                                              draft={newRequirement(InformationRequestRequirementType.RESPONSE_ATTESTATION, "recorded-assertion", "Confirm the records")}
                                              readOnly={false}
                                              onChange={vi.fn()}/>);

        expect(screen.getAllByRole("checkbox").map(checkbox => checkbox.getAttribute("aria-label") ??
            document.querySelector(`label[for="${checkbox.id}"]`)?.textContent))
            .toEqual(["Contributor", "Preparer", "Attestor"]);
    });

    it("asks for an acting role when a stored draft still names Subject", () => {
        render(<RequirementAnsweringFields id={"stored-requirement-answering"}
                                           draft={{
                                               ...newRequirement(InformationRequestRequirementType.DOCUMENT, "supporting-record", "Provide the record"),
                                               contributorRole: InformationRequestContributorRole.SUBJECT,
                                           }}
                                           readOnly={false}
                                           onChange={vi.fn()}
                                           onDraftReplaced={vi.fn()}/>);

        expect((screen.getByLabelText("Answered by") as HTMLSelectElement).value).toBe("");
        expect(screen.getByRole("option", {name: "Choose an acting role"})).toBeTruthy();
    });
});
