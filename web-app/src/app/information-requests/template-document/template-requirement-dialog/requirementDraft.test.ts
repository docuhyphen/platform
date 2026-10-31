import {describe, expect, it} from "vitest";
import {
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseMode,
} from "../../../models/models.tsx";
import {newRequirement} from "../templateDraftDocument.ts";
import {withRequiredness, withResponseMode} from "./requirementDraft.ts";

const conditional = {
    ...newRequirement(InformationRequestRequirementType.FIELD, "recorded-note", "Provide the note"),
    requiredness: InformationRequestRequiredness.CONDITIONAL,
    conditionalRuleKey: "when-note",
};

describe("requirementDraft", () => {
    it("drops the condition when a requirement stops being conditional", () => {
        expect(withRequiredness(conditional, InformationRequestRequiredness.REQUIRED)).toEqual({
            ...conditional,
            requiredness: InformationRequestRequiredness.REQUIRED,
            conditionalRuleKey: undefined,
        });
        expect(withRequiredness(conditional, InformationRequestRequiredness.OPTIONAL).conditionalRuleKey).toBeUndefined();
        expect(withRequiredness(conditional, InformationRequestRequiredness.CONDITIONAL).conditionalRuleKey).toBe("when-note");
    });

    it("drops the condition when the party can no longer answer", () => {
        const viewOnly = withResponseMode(conditional, InformationRequestResponseMode.VIEW_ONLY);

        expect(viewOnly.requiredness).toBe(InformationRequestRequiredness.OPTIONAL);
        expect(viewOnly.conditionalRuleKey).toBeUndefined();
    });
});
