import {useState} from "react";
import {Button, Field, Text, Textarea} from "@fluentui/react-components";
import {InformationRequestResponseDisposition} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {dispositionLabels} from "../../shared/informationRequestLabels.ts";
import {AttestationExceptionInput, AttestationExceptionSettings} from "../attestationException.ts";
import {useSubmissionAttestationExceptionStyles} from "./SubmissionAttestationExceptionStyles.tsx";

interface Props
{
    id: string;
    label: string;
    requirementId: string;
    settings: AttestationExceptionSettings;
    busy: boolean;
    onRecord: (input: AttestationExceptionInput) => void;
}

const REASON_LIMIT = 500;

const SubmissionAttestationException = ({id, label, requirementId, settings, busy, onRecord}: Props) =>
{
    const styles = useSubmissionAttestationExceptionStyles();
    const [editing, setEditing] = useState(false);
    const [disposition, setDisposition] = useState<InformationRequestResponseDisposition | "">(settings.current?.disposition ?? "");
    const [reason, setReason] = useState(settings.current?.reason ?? "");
    const current = settings.current;
    const options = settings.permitted.map(value => ({value, label: dispositionLabels[value]}));
    const record = () =>
    {
        if (!disposition) return;
        onRecord({requirementId, disposition, reason: reason.trim()});
        setEditing(false);
    };

    return (
        <div id={`${id}-exception-block`}
             className={styles.exception}>
            {current && (
                <Text id={`${id}-exception-recorded`}
                      className={styles.detail}>
                    {`Exception recorded: ${dispositionLabels[current.disposition]}.${current.reason ? ` Reason: ${current.reason}.` : ""}`}
                </Text>
            )}
            {!editing && (
                <div id={`${id}-exception-actions`}
                     className={styles.actions}>
                    <Button id={`${id}-exception`}
                            appearance={"secondary"}
                            shape={"circular"}
                            aria-label={current ? `Change the exception for ${label}` : `Record an exception for ${label}`}
                            disabled={busy}
                            onClick={() => setEditing(true)}>
                        {current ? "Change exception" : "Record an exception"}
                    </Button>
                </div>
            )}
            {editing && (
                <>
                    <ChoiceSelect<InformationRequestResponseDisposition> id={`${id}-exception-choice`}
                                                                         label={"Exception"}
                                                                         value={disposition}
                                                                         placeholder={"Choose an exception"}
                                                                         options={options}
                                                                         disabled={busy}
                                                                         onChange={setDisposition}/>
                    <Field id={`${id}-exception-reason-field`}
                           label={"Reason for the exception"}
                           required>
                        <Textarea id={`${id}-exception-reason`}
                                  value={reason}
                                  maxLength={REASON_LIMIT}
                                  disabled={busy}
                                  onChange={(_, data) => setReason(data.value)}/>
                    </Field>
                    <div id={`${id}-exception-form-actions`}
                         className={styles.actions}>
                        <Button id={`${id}-exception-record`}
                                appearance={"primary"}
                                shape={"circular"}
                                aria-label={`Record exception for ${label}`}
                                disabled={busy || !disposition || !reason.trim()}
                                onClick={record}>
                            Record exception
                        </Button>
                        <Button id={`${id}-exception-cancel`}
                                appearance={"secondary"}
                                shape={"circular"}
                                aria-label={`Keep ${label} without an exception`}
                                disabled={busy}
                                onClick={() => setEditing(false)}>
                            Cancel
                        </Button>
                    </div>
                </>
            )}
        </div>
    );
};

export default SubmissionAttestationException;
