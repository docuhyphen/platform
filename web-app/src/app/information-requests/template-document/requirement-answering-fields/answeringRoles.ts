import {InformationRequestContributorRole, InformationRequestRequirementType} from "../../../models/models.tsx";
import {ChoiceOption, optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import {contributorRoleLabels} from "../templateAuthoringLabels.ts";
import {RequirementDraft} from "../template-requirement-dialog/requirementDraft.ts";

type AnsweringRole = Exclude<InformationRequestContributorRole, InformationRequestContributorRole.SUBJECT>;

export const answeringRoleOptions = (requirementType: InformationRequestRequirementType): ChoiceOption<AnsweringRole>[] =>
    optionsFrom(contributorRoleLabels).filter(option =>
        requirementType === InformationRequestRequirementType.RESPONSE_ATTESTATION ||
        option.value !== InformationRequestContributorRole.ATTESTOR);

export const answeringRoleOf = (draft: RequirementDraft): AnsweringRole | "" => {
    const role = draft.contributorRole ?? InformationRequestContributorRole.CONTRIBUTOR;
    return answeringRoleOptions(draft.requirementType).find(option => option.value === role)?.value ?? "";
};
