import {useState} from "react";
import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {
    DefineInformationRequestClockPolicyRequest,
    InformationRequestClockPolicyDefinitionRequest,
    InformationRequestClockPolicyDto,
    InformationRequestOwnerType,
} from "../../../models/models.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {isMachineKey, machineKeyProblem} from "../../shared/machineKey.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import ClockPolicyCalendarFields from "../clock-policy-calendar-fields/ClockPolicyCalendarFields.tsx";
import ClockPolicyNoticeFields from "../clock-policy-notice-fields/ClockPolicyNoticeFields.tsx";
import ClockPolicyTimingFields from "../clock-policy-timing-fields/ClockPolicyTimingFields.tsx";
import {
    ClockPolicyForm,
    ClockPolicyProblems,
    clockPolicyProblems,
    defaultClockPolicyForm,
    definitionFromForm,
    formFromVersion,
} from "../clock-policies/clockPolicyForm.ts";
import {latestVersion} from "../clock-policies/clockPolicyLabels.ts";
import {ClockPolicyRefusal} from "../clock-policies/useClockPolicies.ts";
import {clockPolicyNeeds} from "./clockPolicyNeeds.ts";

interface ClockPolicyDialogProps
{
    policy?: InformationRequestClockPolicyDto;
    ownerType: InformationRequestOwnerType;
    busy: boolean;
    onDefine: (request: DefineInformationRequestClockPolicyRequest) => Promise<ClockPolicyRefusal | null>;
    onPublish: (policy: InformationRequestClockPolicyDto, definition: InformationRequestClockPolicyDefinitionRequest) => Promise<ClockPolicyRefusal | null>;
    onDone: () => void;
}

const KEY_INPUT_ID = "information-request-clock-policy-key";
const KEY_TAKEN = "INFORMATION_REQUEST_CLOCK_POLICY_KEY_TAKEN";

const initialForm = (policy?: InformationRequestClockPolicyDto): ClockPolicyForm =>
{
    const latest = policy ? latestVersion(policy.versions) : undefined;
    return latest ? formFromVersion(latest) : defaultClockPolicyForm(Intl.DateTimeFormat().resolvedOptions().timeZone);
};

const ClockPolicyDialog = ({policy, ownerType, busy, onDefine, onPublish, onDone}: ClockPolicyDialogProps) =>
{
    const [form, setForm] = useState<ClockPolicyForm>(() => initialForm(policy));
    const [edited, setEdited] = useState<Set<keyof ClockPolicyForm>>(new Set());
    const [policyKey, setPolicyKey] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [refusal, setRefusal] = useState<string | null>(null);
    const [keyRefusal, setKeyRefusal] = useState<string | null>(null);
    const problems = clockPolicyProblems(form);
    const shown: ClockPolicyProblems = Object.fromEntries(
        Object.entries(problems).filter(([field]) => edited.has(field as keyof ClockPolicyForm)),
    );
    const definition = definitionFromForm(form);
    const missing = policy ? [] : [...(isMachineKey(policyKey) ? [] : ["Key"]), ...(displayName.trim() ? [] : ["Name"])];
    const needs = clockPolicyNeeds(policy ? "Publish version" : "Create policy", missing, problems);

    const change = <K extends keyof ClockPolicyForm>(field: K, value: ClockPolicyForm[K]) =>
    {
        setForm(current => ({...current, [field]: value}));
        setEdited(current => new Set(current).add(field));
    };

    const confirm = async () =>
    {
        if (!definition) return;
        setRefusal(null);
        setKeyRefusal(null);
        const refused = policy
            ? await onPublish(policy, definition)
            : await onDefine({policyKey: policyKey.trim(), displayName: displayName.trim(), definition});
        if (!refused) {
            onDone();
            return;
        }
        if (refused.code !== KEY_TAKEN) {
            setRefusal(refused.message);
            return;
        }
        setKeyRefusal(refused.message);
        window.setTimeout(() => window.document.getElementById(KEY_INPUT_ID)?.focus(), 0);
    };

    return (
        <EditorDialog id={"information-request-clock-policy-dialog"}
                      title={policy ? `New version of ${policy.displayName}` : "New due date policy"}
                      confirmLabel={policy ? "Publish version" : "Create policy"}
                      busy={busy}
                      wide={true}
                      confirmDisabled={!definition || missing.length > 0}
                      onConfirm={() => void confirm()}
                      onDismiss={onDone}>
            {refusal && (
                <MessageBar id={"information-request-clock-policy-refusal"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-clock-policy-refusal-body"}>{refusal}</MessageBarBody>
                </MessageBar>
            )}
            {!policy && (
                <>
                    <TextField id={KEY_INPUT_ID}
                               label={"Key"}
                               hint={"A short lowercase key that names the policy, such as standard-response."}
                               required={true}
                               value={policyKey}
                               maxLength={128}
                               validationMessage={keyRefusal ?? machineKeyProblem(policyKey)}
                               onChange={value => {
                                   setPolicyKey(value);
                                   setKeyRefusal(null);
                               }}/>
                    <TextField id={"information-request-clock-policy-name"}
                               label={"Name"}
                               required={true}
                               value={displayName}
                               maxLength={200}
                               onChange={setDisplayName}/>
                </>
            )}
            <ClockPolicyCalendarFields form={form}
                                       problems={shown}
                                       onChange={change}/>
            <ClockPolicyTimingFields form={form}
                                     problems={shown}
                                     onChange={change}/>
            <ClockPolicyNoticeFields form={form}
                                     ownerType={policy?.ownerType ?? ownerType}
                                     onChange={change}/>
            {needs && (
                <Text id={"information-request-clock-policy-needs"}
                      aria-live={"polite"}>
                    {needs}
                </Text>
            )}
        </EditorDialog>
    );
};

export default ClockPolicyDialog;
