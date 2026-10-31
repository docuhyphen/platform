import {Field, Input, Textarea} from "@fluentui/react-components";
import {InformationRequestExternalSignatureReferencePolicy} from "../../../models/models.tsx";

interface Props
{
    id: string;
    referencePolicy: InformationRequestExternalSignatureReferencePolicy;
    refusing: boolean;
    busy: boolean;
    reference: string;
    reason: string;
    onReference: (reference: string) => void;
    onReason: (reason: string) => void;
}

const REASON_LIMIT = 500;

const SubmissionAttestationInputs = ({id, referencePolicy, refusing, busy, reference, reason, onReference, onReason}: Props) => (
    <>
        {referencePolicy !== InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED && (
            <Field id={`${id}-reference-field`}
                   label={"Signature reference"}
                   required={referencePolicy === InformationRequestExternalSignatureReferencePolicy.REQUIRED}
                   hint={"A reference to a signature made elsewhere, recorded as given."}>
                <Input id={`${id}-reference`}
                       value={reference}
                       maxLength={512}
                       disabled={busy}
                       onChange={(_, data) => onReference(data.value)}/>
            </Field>
        )}
        {refusing && (
            <Field id={`${id}-reason-field`}
                   label={"Reason for refusing"}
                   required>
                <Textarea id={`${id}-reason`}
                          value={reason}
                          maxLength={REASON_LIMIT}
                          disabled={busy}
                          onChange={(_, data) => onReason(data.value)}/>
            </Field>
        )}
    </>
);

export default SubmissionAttestationInputs;
