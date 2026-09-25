import {useState} from "react";
import {Badge, Button, Field, Input, Text, Textarea} from "@fluentui/react-components";
import {
    InformationRequestAttestationDecision,
    InformationRequestAttestationStatusDto,
    InformationRequestExternalSignatureReferencePolicy,
} from "../../../models/models.tsx";
import {attestationStatePresentation, roleLabel} from "../submissionLabels.ts";
import {AttestationInput} from "../useInformationRequestSubmission.ts";
import {useSubmissionAttestationCardStyles} from "./SubmissionAttestationCardStyles.tsx";

interface Props
{
    status: InformationRequestAttestationStatusDto;
    busy: boolean;
    onAttest: (input: AttestationInput) => void;
}

const REASON_LIMIT = 500;

const SubmissionAttestationCard = ({status, busy, onAttest}: Props) =>
{
    const styles = useSubmissionAttestationCardStyles();
    const [refusing, setRefusing] = useState(false);
    const [reason, setReason] = useState("");
    const [reference, setReference] = useState("");
    const id = `information-request-attestation-${status.requirementId}`;
    const state = attestationStatePresentation[status.state];
    const referencePolicy = status.externalSignatureReference;
    const referenceMissing = referencePolicy === InformationRequestExternalSignatureReferencePolicy.REQUIRED && !reference.trim();
    const externalSignatureReference = referencePolicy === InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED
        ? undefined
        : reference.trim() || undefined;

    return (
        <article id={id}
                 className={styles.card}>
            <div id={`${id}-header`}
                 className={styles.header}>
                <Text id={`${id}-prompt`}
                      weight={"semibold"}>
                    {status.prompt}
                </Text>
                <Badge id={`${id}-state`}
                       appearance={"tint"}
                       color={state.color}>
                    {state.label}
                </Badge>
            </div>
            <Text id={`${id}-progress`}
                  className={styles.detail}>
                {`${status.assentCount} of ${status.requiredAssentCount} confirmations given`}
                {status.missingRoles.length > 0 && `; still needed from ${status.missingRoles.map(roleLabel).join(", ")}`}
            </Text>
            {status.callerDecision && (
                <Text id={`${id}-caller-decision`}
                      className={styles.detail}>
                    {status.callerDecision === InformationRequestAttestationDecision.ASSENTED ? "You confirmed this." : "You refused this."}
                </Text>
            )}
            {status.callerCanAttest && referencePolicy !== InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED && (
                <Field id={`${id}-reference-field`}
                       label={"Signature reference"}
                       required={referencePolicy === InformationRequestExternalSignatureReferencePolicy.REQUIRED}
                       hint={"A reference to a signature made elsewhere, recorded as given."}>
                    <Input id={`${id}-reference`}
                           value={reference}
                           disabled={busy}
                           onChange={(_, data) => setReference(data.value)}/>
                </Field>
            )}
            {status.callerCanAttest && refusing && (
                <Field id={`${id}-reason-field`}
                       label={"Reason for refusing"}
                       required>
                    <Textarea id={`${id}-reason`}
                              value={reason}
                              maxLength={REASON_LIMIT}
                              disabled={busy}
                              onChange={(_, data) => setReason(data.value)}/>
                </Field>
            )}
            {status.callerCanAttest && (
                <div id={`${id}-actions`}
                     className={styles.actions}>
                    <Button id={`${id}-confirm`}
                            appearance={refusing ? "secondary" : "primary"}
                            shape={"circular"}
                            disabled={busy || referenceMissing}
                            onClick={() => refusing
                                ? setRefusing(false)
                                : onAttest({
                                    requirementId: status.requirementId,
                                    decision: InformationRequestAttestationDecision.ASSENTED,
                                    externalSignatureReference,
                                })}>
                        {refusing ? "Keep" : "Confirm"}
                    </Button>
                    <Button id={`${id}-refuse`}
                            appearance={refusing ? "primary" : "secondary"}
                            shape={"circular"}
                            disabled={busy || (refusing && !reason.trim())}
                            onClick={() => refusing
                                ? onAttest({
                                    requirementId: status.requirementId,
                                    decision: InformationRequestAttestationDecision.REFUSED,
                                    refusalReason: reason.trim(),
                                })
                                : setRefusing(true)}>
                        {refusing ? "Refuse" : "Refuse to confirm"}
                    </Button>
                </div>
            )}
        </article>
    );
};

export default SubmissionAttestationCard;
