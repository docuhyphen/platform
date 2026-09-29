import {useState} from "react";
import {MessageBar, MessageBarBody} from "@fluentui/react-components";
import {
    DefineInformationRequestClockPolicyRequest,
    InformationRequestClockPolicyDefinitionRequest,
    InformationRequestClockPolicyDto,
} from "../../../models/models.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {isMachineKey, machineKeyProblem} from "../../shared/machineKey.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import ClockPolicyCalendarFields from "../clock-policy-calendar-fields/ClockPolicyCalendarFields.tsx";
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

interface ClockPolicyDialogProps
{
    policy?: InformationRequestClockPolicyDto;
    busy: boolean;
    onDefine: (request: DefineInformationRequestClockPolicyRequest) => Promise<string | null>;
    onPublish: (policy: InformationRequestClockPolicyDto, definition: InformationRequestClockPolicyDefinitionRequest) => Promise<string | null>;
    onDone: () => void;
}

const initialForm = (policy?: InformationRequestClockPolicyDto): ClockPolicyForm =>
{
    const latest = policy ? latestVersion(policy.versions) : undefined;
    return latest ? formFromVersion(latest) : defaultClockPolicyForm(Intl.DateTimeFormat().resolvedOptions().timeZone);
};

const ClockPolicyDialog = ({policy, busy, onDefine, onPublish, onDone}: ClockPolicyDialogProps) =>
{
    const [form, setForm] = useState<ClockPolicyForm>(() => initialForm(policy));
    const [edited, setEdited] = useState<Set<keyof ClockPolicyForm>>(new Set());
    const [policyKey, setPolicyKey] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [refusal, setRefusal] = useState<string | null>(null);
    const problems = clockPolicyProblems(form);
    const shown: ClockPolicyProblems = Object.fromEntries(
        Object.entries(problems).filter(([field]) => edited.has(field as keyof ClockPolicyForm)),
    );
    const definition = definitionFromForm(form);
    const named = Boolean(policy) || (isMachineKey(policyKey) && displayName.trim().length > 0);

    const change = <K extends keyof ClockPolicyForm>(field: K, value: ClockPolicyForm[K]) =>
    {
        setForm(current => ({...current, [field]: value}));
        setEdited(current => new Set(current).add(field));
    };

    const confirm = async () =>
    {
        if (!definition) return;
        setRefusal(null);
        const refused = policy
            ? await onPublish(policy, definition)
            : await onDefine({policyKey: policyKey.trim(), displayName: displayName.trim(), definition});
        if (refused) setRefusal(refused);
        else onDone();
    };

    return (
        <EditorDialog id={"information-request-clock-policy-dialog"}
                      title={policy ? `New version of ${policy.displayName}` : "New due date policy"}
                      confirmLabel={policy ? "Publish version" : "Create policy"}
                      busy={busy}
                      wide={true}
                      confirmDisabled={!definition || !named}
                      onConfirm={() => void confirm()}
                      onDismiss={onDone}>
            {refusal && (
                <MessageBar id={"information-request-clock-policy-refusal"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody>{refusal}</MessageBarBody>
                </MessageBar>
            )}
            {!policy && (
                <>
                    <TextField id={"information-request-clock-policy-key"}
                               label={"Key"}
                               hint={"A short lowercase key that names the policy, such as standard-response."}
                               required={true}
                               value={policyKey}
                               maxLength={128}
                               validationMessage={machineKeyProblem(policyKey)}
                               onChange={setPolicyKey}/>
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
        </EditorDialog>
    );
};

export default ClockPolicyDialog;
