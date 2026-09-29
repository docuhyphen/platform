import {Checkbox} from "@fluentui/react-components";
import {
    InformationRequestEvidenceConformancePolicy,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestTemplateEvidencePolicyRequest,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import NumberField from "../../shared/number-field/NumberField.tsx";
import {conformancePolicyLabels, waiverPolicyLabels} from "../templateAuthoringLabels.ts";
import {defaultEvidencePolicy} from "../templateDraftDocument.ts";
import {RequirementFieldsProps} from "../template-requirement-dialog/requirementDraft.ts";
import {useRequirementEvidenceFieldsStyles} from "./RequirementEvidenceFieldsStyles.tsx";

const BYTES_PER_MEGABYTE = 1024 * 1024;

type CountedPolicyKey =
    | "minimumFileCount"
    | "maximumFileCount"
    | "minimumPageCount"
    | "maximumPageCount"
    | "maximumIssueAgeDays"
    | "minimumRemainingValidityDays"
    | "minimumCoverageDays";

const megabytes = (bytes?: number): number | undefined =>
    bytes === undefined ? undefined : Math.round((bytes / BYTES_PER_MEGABYTE) * 100) / 100;

const bytes = (value?: number): number | undefined =>
    value === undefined ? undefined : Math.round(value * BYTES_PER_MEGABYTE);

const RequirementEvidenceFields = ({id, draft, readOnly, onChange}: RequirementFieldsProps) =>
{
    const styles = useRequirementEvidenceFieldsStyles();
    const policy = draft.evidencePolicy ?? defaultEvidencePolicy();
    const set = (change: Partial<InformationRequestTemplateEvidencePolicyRequest>) =>
        onChange({evidencePolicy: {...policy, ...change}});
    const number = (label: string, key: CountedPolicyKey, min = 0) => (
        <NumberField id={`${id}-${key}-input`}
                     label={label}
                     value={policy[key]}
                     min={min}
                     disabled={readOnly}
                     onChange={value => set({[key]: value})}/>
    );

    return (
        <div id={`${id}-evidence`}
             className={styles.grid}>
            {number("Minimum files", "minimumFileCount", 1)}
            {number("Maximum files", "maximumFileCount", 1)}
            <NumberField id={`${id}-file-size-input`}
                         label={"Maximum file size (MB)"}
                         value={megabytes(policy.maximumFileSizeBytes)}
                         min={0}
                         step={0.5}
                         disabled={readOnly}
                         onChange={value => set({maximumFileSizeBytes: bytes(value)})}/>
            <NumberField id={`${id}-total-size-input`}
                         label={"Maximum total size (MB)"}
                         value={megabytes(policy.maximumTotalSizeBytes)}
                         min={0}
                         step={0.5}
                         disabled={readOnly}
                         onChange={value => set({maximumTotalSizeBytes: bytes(value)})}/>
            {number("Minimum pages", "minimumPageCount")}
            {number("Maximum pages", "maximumPageCount", 1)}
            {number("Maximum age since issue (days)", "maximumIssueAgeDays")}
            {number("Minimum validity remaining (days)", "minimumRemainingValidityDays")}
            {number("Minimum coverage (days)", "minimumCoverageDays")}
            <ChoiceSelect id={`${id}-waiver-select`}
                          label={"Waiver"}
                          value={policy.waiverPolicy ?? InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED}
                          options={optionsFrom(waiverPolicyLabels)}
                          disabled={readOnly}
                          onChange={waiverPolicy => set({waiverPolicy})}/>
            <ChoiceSelect id={`${id}-conformance-select`}
                          label={"Conformance"}
                          value={policy.conformancePolicy ?? InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED}
                          options={optionsFrom(conformancePolicyLabels)}
                          disabled={readOnly}
                          onChange={conformancePolicy => set({conformancePolicy})}/>
            <Checkbox id={`${id}-continuity-checkbox`}
                      label={"Coverage periods must be continuous"}
                      checked={policy.coverageContinuityRequired ?? false}
                      disabled={readOnly}
                      className={styles.wide}
                      onChange={(_, data) => set({coverageContinuityRequired: data.checked === true})}/>
        </div>
    );
};

export default RequirementEvidenceFields;
