import {Text} from "@fluentui/react-components";
import {
    InformationRequestAttestationOrdering,
    InformationRequestAuthenticationStrength,
    InformationRequestContributorRole,
    InformationRequestExternalSignatureReferencePolicy,
    InformationRequestTemplateAttestationPolicyRequest,
} from "../../../models/models.tsx";
import CheckList from "../../shared/check-list/CheckList.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import NumberField from "../../shared/number-field/NumberField.tsx";
import {
    attestationOrderingLabels,
    authenticationStrengthLabels,
    contributorRoleLabels,
    signatureReferenceLabels,
} from "../templateAuthoringLabels.ts";
import {isAnswerable, RequirementFieldsProps} from "../template-requirement-dialog/requirementDraft.ts";
import {useRequirementConfirmationFieldsStyles} from "./RequirementConfirmationFieldsStyles.tsx";

const RequirementConfirmationFields = ({id, draft, readOnly, onChange}: RequirementFieldsProps) =>
{
    const styles = useRequirementConfirmationFieldsStyles();
    if (!isAnswerable(draft))
    {
        return (
            <Text id={`${id}-confirmation-unanswerable`}
                  className={styles.muted}>
                The party cannot answer this requirement, so nobody makes this confirmation.
            </Text>
        );
    }
    const policy = draft.attestationPolicy ?? {};
    const roles = policy.requiredRoles?.length
        ? policy.requiredRoles
        : [draft.contributorRole ?? InformationRequestContributorRole.CONTRIBUTOR];
    const set = (change: Partial<InformationRequestTemplateAttestationPolicyRequest>) =>
        onChange({attestationPolicy: {...policy, ...change}});

    return (
        <div id={`${id}-confirmation`}
             className={styles.grid}>
            <div id={`${id}-confirmation-roles`}
                 className={styles.wide}>
                <CheckList id={`${id}-confirmation-role-list`}
                           label={"Roles that confirm"}
                           options={optionsFrom(contributorRoleLabels)}
                           selected={roles}
                           disabled={readOnly}
                           onChange={requiredRoles => set({requiredRoles})}/>
            </div>
            <ChoiceSelect id={`${id}-confirmation-order-select`}
                          label={"Order"}
                          value={policy.ordering ?? InformationRequestAttestationOrdering.ANY_ORDER}
                          options={optionsFrom(attestationOrderingLabels)}
                          disabled={readOnly}
                          onChange={ordering => set({ordering})}/>
            <NumberField id={`${id}-confirmation-assents-input`}
                         label={"Minimum confirmations"}
                         value={policy.minimumAssentCount}
                         min={1}
                         hint={`Leave empty to need one from each role (${roles.length}).`}
                         disabled={readOnly}
                         onChange={minimumAssentCount => set({minimumAssentCount})}/>
            <ChoiceSelect id={`${id}-confirmation-strength-select`}
                          label={"Minimum sign-in strength"}
                          value={policy.minimumAuthenticationStrength ?? InformationRequestAuthenticationStrength.VERIFIED_CONTACT}
                          options={optionsFrom(authenticationStrengthLabels)}
                          disabled={readOnly}
                          onChange={minimumAuthenticationStrength => set({minimumAuthenticationStrength})}/>
            <NumberField id={`${id}-confirmation-validity-input`}
                         label={"Valid for (hours)"}
                         value={policy.validityHours}
                         min={1}
                         hint={"Leave empty for a confirmation that does not lapse."}
                         disabled={readOnly}
                         onChange={validityHours => set({validityHours})}/>
            <ChoiceSelect id={`${id}-confirmation-signature-select`}
                          label={"External signature reference"}
                          value={policy.externalSignatureReference ?? InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED}
                          options={optionsFrom(signatureReferenceLabels)}
                          disabled={readOnly}
                          onChange={externalSignatureReference => set({externalSignatureReference})}/>
        </div>
    );
};

export default RequirementConfirmationFields;
