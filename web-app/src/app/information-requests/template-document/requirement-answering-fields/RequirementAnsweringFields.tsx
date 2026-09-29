import {Text} from "@fluentui/react-components";
import {
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
} from "../../../models/models.tsx";
import CheckList from "../../shared/check-list/CheckList.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {
    contributorRoleLabels,
    dispositionLabels,
    requirednessLabels,
    responseModeLabels,
    reviewPolicyLabels,
} from "../templateAuthoringLabels.ts";
import {
    isAnswerable,
    RequirementDraft,
    RequirementFieldsProps,
    withResponseMode,
} from "../template-requirement-dialog/requirementDraft.ts";
import {useRequirementAnsweringFieldsStyles} from "./RequirementAnsweringFieldsStyles.tsx";

interface RequirementAnsweringFieldsProps extends RequirementFieldsProps
{
    onDraftReplaced: (draft: RequirementDraft) => void;
}

const ONCE_FOR_REQUEST = "";

const RequirementAnsweringFields = ({id, draft, readOnly, onChange, onDraftReplaced}: RequirementAnsweringFieldsProps) =>
{
    const styles = useRequirementAnsweringFieldsStyles();
    const {document} = useTemplateDocument();
    const answerable = isAnswerable(draft);
    const conditional = draft.requiredness === InformationRequestRequiredness.CONDITIONAL;

    return (
        <div id={`${id}-answering`}
             className={styles.grid}>
            <ChoiceSelect id={`${id}-mode-select`}
                          label={"Response mode"}
                          value={draft.responseMode ?? InformationRequestResponseMode.PROVIDE}
                          options={optionsFrom(responseModeLabels)}
                          disabled={readOnly}
                          onChange={mode => onDraftReplaced(withResponseMode(draft, mode))}/>
            <ChoiceSelect id={`${id}-requiredness-select`}
                          label={"Requiredness"}
                          value={draft.requiredness ?? InformationRequestRequiredness.OPTIONAL}
                          options={optionsFrom(requirednessLabels)}
                          disabled={readOnly || !answerable}
                          onChange={requiredness => onChange({requiredness})}/>
            {conditional && (
                <ChoiceSelect id={`${id}-condition-select`}
                              label={"Condition"}
                              value={draft.conditionalRuleKey ?? ""}
                              placeholder={"Choose a condition"}
                              options={document.conditionRules.map(rule => ({value: rule.ruleKey, label: rule.ruleKey}))}
                              disabled={readOnly}
                              hint={document.conditionRules.length === 0 ? "Add a condition on the Conditions tab first." : undefined}
                              onChange={conditionalRuleKey => onChange({conditionalRuleKey})}/>
            )}
            <ChoiceSelect id={`${id}-role-select`}
                          label={"Answered by"}
                          value={draft.contributorRole ?? InformationRequestContributorRole.CONTRIBUTOR}
                          options={optionsFrom(contributorRoleLabels)}
                          disabled={readOnly}
                          onChange={contributorRole => onChange({contributorRole})}/>
            <ChoiceSelect id={`${id}-review-select`}
                          label={"Review"}
                          value={draft.reviewPolicy ?? InformationRequestReviewPolicy.NOT_REQUIRED}
                          options={optionsFrom(reviewPolicyLabels)}
                          disabled={readOnly}
                          onChange={reviewPolicy => onChange({reviewPolicy})}/>
            <ChoiceSelect id={`${id}-anchor-select`}
                          label={"Repeats"}
                          value={draft.occurrenceAnchorKey ?? ONCE_FOR_REQUEST}
                          options={[
                              {value: ONCE_FOR_REQUEST, label: "Once for the whole request"},
                              ...document.groups.map(group => ({value: group.groupKey, label: `Once per ${group.groupKey}`})),
                          ]}
                          disabled={readOnly}
                          onChange={anchor => onChange({occurrenceAnchorKey: anchor || undefined})}/>
            <TextField id={`${id}-compartment-input`}
                       label={"Confidentiality compartment"}
                       value={draft.confidentialityCompartmentKey ?? ""}
                       hint={"Answers in a compartment are shown only to parties cleared for it."}
                       disabled={readOnly}
                       onChange={key => onChange({confidentialityCompartmentKey: key || undefined})}/>
            <div id={`${id}-dispositions`}
                 className={styles.wide}>
                {answerable
                    ? (
                        <CheckList id={`${id}-disposition-list`}
                                   label={"Permitted answers"}
                                   options={optionsFrom(dispositionLabels)
                                       .filter(option => option.value !== InformationRequestResponseDisposition.NOT_ANSWERED)}
                                   selected={draft.permittedDispositions ?? []}
                                   disabled={readOnly}
                                   hint={"A waived answer also needs a waiver rule on the Evidence tab."}
                                   onChange={permittedDispositions => onChange({permittedDispositions})}/>
                    )
                    : (
                        <Text id={`${id}-dispositions-hidden`}
                              className={styles.muted}>
                            The party cannot answer this requirement, so it offers no answers to choose from.
                        </Text>
                    )}
            </div>
        </div>
    );
};

export default RequirementAnsweringFields;
