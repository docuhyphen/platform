import {useState} from "react";
import {Badge, Text} from "@fluentui/react-components";
import {
    InformationRequestAttestationDecision,
    InformationRequestAttestationStatusDto,
    InformationRequestAuthenticationStrength,
    InformationRequestExternalSignatureReferencePolicy,
} from "../../../models/models.tsx";
import SubmissionAttestationActions from "../submission-attestation-actions/SubmissionAttestationActions.tsx";
import SubmissionAttestationException from "../submission-attestation-exception/SubmissionAttestationException.tsx";
import SubmissionAttestationInputs from "./SubmissionAttestationInputs.tsx";
import SubmissionAttestationNotes from "../submission-attestation-notes/SubmissionAttestationNotes.tsx";
import SubmissionAttestationPolicy from "../submission-attestation-policy/SubmissionAttestationPolicy.tsx";
import AttestingPartyChoice from "../attesting-party-choice/AttestingPartyChoice.tsx";
import {AttestationExceptionInput, AttestationExceptionSettings} from "../attestationException.ts";
import {attestationStatePresentation, roleLabel} from "../submissionLabels.ts";
import {AttestationInput} from "../useInformationRequestSubmission.ts";
import {useSubmissionAttestationCardStyles} from "./SubmissionAttestationCardStyles.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";

interface Props
{
    status: InformationRequestAttestationStatusDto;
    busy: boolean;
    contactOnly?: boolean;
    label?: string;
    exception?: AttestationExceptionSettings;
    onAttest: (input: AttestationInput) => void;
    onRecordException?: (input: AttestationExceptionInput) => void;
}

const SubmissionAttestationCard = ({
                                       status,
                                       busy: pending,
                                       contactOnly = false,
                                       label = status.prompt,
                                       exception,
                                       onAttest,
                                       onRecordException,
                                   }: Props) =>
{
    const styles = useSubmissionAttestationCardStyles();
    const contactBlocked = contactOnly && status.minimumAuthenticationStrength !== InformationRequestAuthenticationStrength.VERIFIED_CONTACT;
    const busy = pending || contactBlocked;
    const [refusing, setRefusing] = useState(false);
    const [reason, setReason] = useState("");
    const [reference, setReference] = useState("");
    const [chosenPartyId, setChosenPartyId] = useState<string | undefined>(undefined);
    const parties = status.attestingParties ?? [];
    const choosingParty = parties.length > 1;
    const partyId = choosingParty ? chosenPartyId : undefined;
    const partyMissing = choosingParty && !partyId;
    const id = `information-request-attestation-${status.requirementId}`;
    const state = attestationStatePresentation[status.state];
    const referencePolicy = status.externalSignatureReference;
    const referenceMissing = referencePolicy === InformationRequestExternalSignatureReferencePolicy.REQUIRED && !reference.trim();
    const waiting = status.waitingForRoles.length > 0;
    const externalSignatureReference = referencePolicy === InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED
        ? undefined
        : reference.trim() || undefined;

    return (
        <article id={id}
                 aria-labelledby={`${id}-prompt`}
                 className={styles.card}>
            <div id={`${id}-header`}
                 className={styles.header}>
                <Text id={`${id}-prompt`}
                      weight={"semibold"}>
                    {label}
                </Text>
                <Badge id={`${id}-state`}
                       appearance={"tint"}
                       color={state.color}>
                    {state.label}
                </Badge>
            </div>
            <SubmissionAttestationPolicy id={id}
                                         status={status}
                                         contactBlocked={contactBlocked}/>
            <Text id={`${id}-progress`}
                  className={styles.detail}>
                {`${formatInformationRequestCount(status.assentCount)} of ${formatInformationRequestCount(status.requiredAssentCount)} confirmations given`}
                {status.missingRoles.length > 0 && `; still needed from ${status.missingRoles.map(roleLabel).join(", ")}`}
            </Text>
            <SubmissionAttestationNotes id={id}
                                        status={status}/>
            {status.callerCanAttest && choosingParty && (
                <AttestingPartyChoice id={id}
                                      parties={parties}
                                      value={chosenPartyId}
                                      disabled={busy}
                                      onChange={setChosenPartyId}/>
            )}
            {status.callerCanAttest && (
                <SubmissionAttestationInputs id={id}
                                             referencePolicy={referencePolicy}
                                             refusing={refusing}
                                             busy={busy}
                                             reference={reference}
                                             reason={reason}
                                             onReference={setReference}
                                             onReason={setReason}/>
            )}
            {status.callerCanAttest && <SubmissionAttestationActions id={id}
                                                                     label={label}
                                                                     busy={busy}
                                                                     refusing={refusing}
                                                                     canConfirm={!referenceMissing && !waiting && !partyMissing}
                                                                     canRefuse={!partyMissing && (!refusing || !!reason.trim())}
                                                                     onConfirm={() => refusing ? setRefusing(false) : onAttest({
                                                                         requirementId: status.requirementId,
                                                                         decision: InformationRequestAttestationDecision.ASSENTED,
                                                                         externalSignatureReference,
                                                                         partyId
                                                                     })}
                                                                     onRefuse={() => refusing ? onAttest({
                                                                         requirementId: status.requirementId,
                                                                         decision: InformationRequestAttestationDecision.REFUSED,
                                                                         refusalReason: reason.trim(),
                                                                         partyId
                                                                     }) : setRefusing(true)}/>}
            {status.callerCanAttest && exception && onRecordException && (
                <SubmissionAttestationException id={id}
                                                label={label}
                                                requirementId={status.requirementId}
                                                settings={exception}
                                                busy={busy}
                                                onRecord={onRecordException}/>
            )}
        </article>
    );
};

export default SubmissionAttestationCard;
