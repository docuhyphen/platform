/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseMode,
    InformationRequestTemplateConditionRuleRequest,
    InformationRequestTemplateGroupRequest,
} from "../../../models/models.tsx";
import RequirementAnsweringFields from "./RequirementAnsweringFields.tsx";

vi.mock("../TemplateDocumentContext.ts", () => ({
    useTemplateDocument: () => ({document: {groups: [] as InformationRequestTemplateGroupRequest[], conditionRules: [] as InformationRequestTemplateConditionRuleRequest[]}}),
}));

describe("RequirementAnsweringFields", () => {
    afterEach(cleanup);

    it("explains that a confidentiality compartment asks people to verify again", () => {
        render(<RequirementAnsweringFields id={"requirement"}
                                           draft={{
                                               requirementKey: "item-detail",
                                               requirementType: InformationRequestRequirementType.FIELD,
                                               prompt: "Describe the item",
                                               responseMode: InformationRequestResponseMode.PROVIDE,
                                           }}
                                           readOnly={false}
                                           onChange={vi.fn()}
                                           onDraftReplaced={vi.fn()}/>);

        expect(screen.getByText("Any label marks this Requirement as protected: people must verify again to see or answer it."))
            .toBeTruthy();
        expect(screen.queryByText(/cleared for it/)).toBeNull();
    });

    it("points to the Evidence tab for a waiver rule only on a Document requirement", () => {
        const draft = {
            requirementKey: "item-detail",
            requirementType: InformationRequestRequirementType.FIELD,
            prompt: "Describe the item",
            responseMode: InformationRequestResponseMode.PROVIDE,
        };
        const hint = "A waived answer also needs a waiver rule on the Evidence tab.";
        const {rerender} = render(<RequirementAnsweringFields id={"requirement"}
                                                              draft={draft}
                                                              readOnly={false}
                                                              onChange={vi.fn()}
                                                              onDraftReplaced={vi.fn()}/>);

        expect(screen.queryByText(hint)).toBeNull();
        rerender(<RequirementAnsweringFields id={"requirement"}
                                             draft={{
                                                 ...draft,
                                                 requirementType: InformationRequestRequirementType.DOCUMENT
                                             }}
                                             readOnly={false}
                                             onChange={vi.fn()}
                                             onDraftReplaced={vi.fn()}/>);
        expect(screen.getByText(hint)).toBeTruthy();
    });

    it("drops the condition when the requirement stops being asked only when it is true", () => {
        const draft = {
            requirementKey: "item-detail",
            requirementType: InformationRequestRequirementType.FIELD,
            prompt: "Describe the item",
            responseMode: InformationRequestResponseMode.PROVIDE,
            requiredness: InformationRequestRequiredness.CONDITIONAL,
            conditionalRuleKey: "when-item",
        };
        const onChange = vi.fn();
        const onDraftReplaced = vi.fn();
        render(<RequirementAnsweringFields id={"requirement"}
                                           draft={draft}
                                           readOnly={false}
                                           onChange={onChange}
                                           onDraftReplaced={onDraftReplaced}/>);

        fireEvent.change(document.getElementById("requirement-requiredness-select")!,
            {target: {value: InformationRequestRequiredness.REQUIRED}});

        expect(onDraftReplaced).toHaveBeenCalledWith({
            ...draft,
            requiredness: InformationRequestRequiredness.REQUIRED,
            conditionalRuleKey: undefined,
        });
        expect(onChange).not.toHaveBeenCalled();
    });
});
