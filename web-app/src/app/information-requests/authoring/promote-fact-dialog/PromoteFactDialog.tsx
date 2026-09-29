import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {InformationRequestAcceptedFactVisibility, PromoteInformationRequestAcceptedFactRequest} from "../../../models/models.tsx";
import CheckList from "../../shared/check-list/CheckList.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {ChoiceOption, optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import DateTimeField from "../../shared/date-time-field/DateTimeField.tsx";
import {instantFromLocalInput} from "../../shared/dateTimeInput.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {isMachineKey, machineKeyProblem} from "../../shared/machineKey.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {factVisibilityLabels} from "../request-outcomes-panel/outcomeLabels.ts";

export type PromotionTerms = Omit<PromoteInformationRequestAcceptedFactRequest, "packageId" | "submissionItemId">;

interface Props
{
    label: string;
    evidenceChoices: ChoiceOption<string>[];
    busy: boolean;
    onConfirm: (terms: PromotionTerms) => void;
    onDismiss: () => void;
}

const later = (value: string, than: string): boolean =>
{
    const end = instantFromLocalInput(value);
    const start = instantFromLocalInput(than);
    return !end || !start || new Date(end) > new Date(start);
};

const PromoteFactDialog = ({label, evidenceChoices, busy, onConfirm, onDismiss}: Props) =>
{
    const [purpose, setPurpose] = useState("");
    const [policyBasis, setPolicyBasis] = useState("");
    const [visibility, setVisibility] = useState(InformationRequestAcceptedFactVisibility.REQUESTING_SIDE);
    const [validFrom, setValidFrom] = useState("");
    const [validTo, setValidTo] = useState("");
    const [expiresAt, setExpiresAt] = useState("");
    const [evidenceVersionIds, setEvidenceVersionIds] = useState<string[]>([]);
    const periodProblem = later(validTo, validFrom) ? undefined : "The valid period ends after it begins.";
    const expired = expiresAt !== "" && !later(expiresAt, new Date().toISOString());
    const ready = isMachineKey(purpose) && isMachineKey(policyBasis) && !periodProblem && !expired;

    const confirm = () =>
    {
        const from = instantFromLocalInput(validFrom);
        const to = instantFromLocalInput(validTo);
        const expires = instantFromLocalInput(expiresAt);
        onConfirm({
            purposeKey: purpose.trim(),
            policyBasisKey: policyBasis.trim(),
            visibility,
            ...(evidenceVersionIds.length > 0 ? {evidenceVersionIds} : {}),
            ...(from ? {validFrom: from} : {}),
            ...(to ? {validTo: to} : {}),
            ...(expires ? {expiresAt: expires} : {}),
        });
    };

    return (
        <EditorDialog id={"information-request-promote-fact-dialog"}
                      title={`Promote ${label}`}
                      confirmLabel={"Promote"}
                      busy={busy}
                      confirmDisabled={!ready}
                      onConfirm={confirm}
                      onDismiss={onDismiss}>
            <Text id={"information-request-promote-fact-explanation"}>
                The submitted value is kept as it was accepted. Later requests about the same subject offer it for
                that purpose, and the respondent confirms it again before it is used.
            </Text>
            <TextField id={"information-request-promote-fact-purpose"}
                       label={"Purpose"}
                       hint={"A short lowercase key naming what the fact may be reused for, such as contact-details."}
                       required={true}
                       value={purpose}
                       maxLength={128}
                       validationMessage={machineKeyProblem(purpose)}
                       onChange={setPurpose}/>
            <TextField id={"information-request-promote-fact-policy-basis"}
                       label={"Reuse policy basis"}
                       hint={"A short lowercase key identifying the policy that permits retaining and reusing this value."}
                       required={true}
                       value={policyBasis}
                       maxLength={128}
                       validationMessage={machineKeyProblem(policyBasis)}
                       onChange={setPolicyBasis}/>
            <ChoiceSelect<InformationRequestAcceptedFactVisibility> id={"information-request-promote-fact-visibility"}
                                                                    label={"Who may see it"}
                                                                    value={visibility}
                                                                    options={optionsFrom<InformationRequestAcceptedFactVisibility>(factVisibilityLabels)}
                                                                    onChange={setVisibility}/>
            {evidenceChoices.length > 0 && (
                <CheckList<string> id={"information-request-promote-fact-evidence"}
                                   label={"Supporting evidence to keep with the fact"}
                                   hint={"Only conforming files that support this answer in the same package are listed."}
                                   options={evidenceChoices}
                                   selected={evidenceVersionIds}
                                   onChange={setEvidenceVersionIds}/>
            )}
            <DateTimeField id={"information-request-promote-fact-valid-from"}
                           label={"Valid from"}
                           hint={"Leave empty to start now."}
                           value={validFrom}
                           onChange={setValidFrom}/>
            <DateTimeField id={"information-request-promote-fact-valid-to"}
                           label={"Valid until"}
                           hint={"Leave empty when the value does not lapse."}
                           value={validTo}
                           validationMessage={periodProblem}
                           onChange={setValidTo}/>
            <DateTimeField id={"information-request-promote-fact-expires"}
                           label={"Stop offering it after"}
                           value={expiresAt}
                           validationMessage={expired ? "Choose a time that has not passed yet." : undefined}
                           onChange={setExpiresAt}/>
        </EditorDialog>
    );
};

export default PromoteFactDialog;
