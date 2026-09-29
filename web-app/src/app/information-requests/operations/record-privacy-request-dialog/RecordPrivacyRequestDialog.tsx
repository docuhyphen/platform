import {useState} from "react";
import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {InformationRequestPrivacyRequestKind, RecordInformationRequestPrivacyRequestRequest} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {isLetterKey, letterKeyProblem} from "../../shared/machineKey.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {privacyKindLabels} from "../privacy/privacyLabels.ts";

interface RecordPrivacyRequestDialogProps
{
    subjectId: string;
    subjectLabel: string;
    busy: boolean;
    onConfirm: (request: RecordInformationRequestPrivacyRequestRequest) => Promise<string | null>;
    onDone: () => void;
}

const RECORDABLE = [
    InformationRequestPrivacyRequestKind.ACCESS,
    InformationRequestPrivacyRequestKind.EXPORT,
    InformationRequestPrivacyRequestKind.RESTRICTION,
    InformationRequestPrivacyRequestKind.DELETION,
];

const EXPLANATIONS: Partial<Record<InformationRequestPrivacyRequestKind, string>> = {
    [InformationRequestPrivacyRequestKind.ACCESS]: "Prepares a record export of every request about this subject for you to review.",
    [InformationRequestPrivacyRequestKind.EXPORT]: "Prepares a record export of every request about this subject to hand over.",
    [InformationRequestPrivacyRequestKind.RESTRICTION]: "Stops answers about this subject from being promoted or reused until the restriction is lifted.",
    [InformationRequestPrivacyRequestKind.DELETION]: "Disposes of every Information Request record about this subject. If any of them must still be kept, "
        + "for example under a preservation hold, nothing is deleted and the request is recorded as refused. This cannot be undone.",
};

const RecordPrivacyRequestDialog = ({subjectId, subjectLabel, busy, onConfirm, onDone}: RecordPrivacyRequestDialogProps) =>
{
    const [kind, setKind] = useState(InformationRequestPrivacyRequestKind.ACCESS);
    const [purpose, setPurpose] = useState("");
    const [basis, setBasis] = useState("");
    const [region, setRegion] = useState("");
    const [refusal, setRefusal] = useState<string | null>(null);
    const exporting = kind === InformationRequestPrivacyRequestKind.EXPORT;
    const ready = isLetterKey(purpose) && isLetterKey(basis);

    const confirm = async () =>
    {
        setRefusal(null);
        const refused = await onConfirm({
            subjectIdentityRefId: subjectId,
            requestKind: kind,
            purposeKey: purpose.trim(),
            policyBasisKey: basis.trim(),
            ...(exporting && region.trim() ? {transferRegion: region.trim()} : {}),
        });
        if (refused) setRefusal(refused);
        else onDone();
    };

    return (
        <EditorDialog id={"information-request-record-privacy-dialog"}
                      title={"Record a privacy request"}
                      confirmLabel={kind === InformationRequestPrivacyRequestKind.DELETION ? "Delete records" : "Record request"}
                      busy={busy}
                      confirmDisabled={!ready}
                      onConfirm={() => void confirm()}
                      onDismiss={onDone}>
            {refusal && (
                <MessageBar id={"information-request-record-privacy-refusal"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody>{refusal}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-record-privacy-subject"}
                  weight={"semibold"}>
                {subjectLabel}
            </Text>
            <ChoiceSelect<InformationRequestPrivacyRequestKind> id={"information-request-record-privacy-kind"}
                                                                label={"Request"}
                                                                value={kind}
                                                                options={RECORDABLE.map(value => ({value, label: privacyKindLabels[value]}))}
                                                                onChange={setKind}/>
            <Text id={"information-request-record-privacy-explanation"}>{EXPLANATIONS[kind]}</Text>
            <TextField id={"information-request-record-privacy-purpose"}
                       label={"Purpose"}
                       hint={"A short lowercase key naming why the request is handled, such as subject-request."}
                       required={true}
                       value={purpose}
                       maxLength={128}
                       validationMessage={letterKeyProblem(purpose)}
                       onChange={setPurpose}/>
            <TextField id={"information-request-record-privacy-basis"}
                       label={"Policy basis"}
                       hint={"A short lowercase key naming the policy that allows it, such as consent."}
                       required={true}
                       value={basis}
                       maxLength={128}
                       validationMessage={letterKeyProblem(basis)}
                       onChange={setBasis}/>
            {exporting && (
                <TextField id={"information-request-record-privacy-region"}
                           label={"Transfer region"}
                           hint={"Where the export is sent, if outside the usual region."}
                           value={region}
                           maxLength={64}
                           onChange={setRegion}/>
            )}
        </EditorDialog>
    );
};

export default RecordPrivacyRequestDialog;
