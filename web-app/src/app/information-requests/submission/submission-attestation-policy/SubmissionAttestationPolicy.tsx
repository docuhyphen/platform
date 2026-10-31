import {Text} from "@fluentui/react-components";
import {
    InformationRequestAttestationDecision,
    InformationRequestAttestationOrdering,
    InformationRequestAttestationStatusDto,
    InformationRequestAuthenticationStrength
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {roleLabel} from "../submissionLabels.ts";
import {useSubmissionAttestationPolicyStyles} from "./SubmissionAttestationPolicyStyles.tsx";

interface Props {
    id: string;
    status: InformationRequestAttestationStatusDto;
    contactBlocked: boolean;
}

const strengthLabels: Record<InformationRequestAuthenticationStrength, string> = {
    [InformationRequestAuthenticationStrength.VERIFIED_CONTACT]: "verified contact details",
    [InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN]: "account sign-in",
    [InformationRequestAuthenticationStrength.MULTI_FACTOR]: "multi-factor verification",
};

const SubmissionAttestationPolicy = ({id, status, contactBlocked}: Props) => {
    const styles = useSubmissionAttestationPolicyStyles();
    return <>
        <Text id={`${id}-identity-policy`}
              className={styles.detail}>
            {`Requires ${strengthLabels[status.minimumAuthenticationStrength]}.`}
        </Text>
        <Text id={`${id}-order-policy`}
              className={styles.detail}>
            {status.ordering === InformationRequestAttestationOrdering.ROLE_SEQUENCE ? "Roles confirm in order." : "Roles may confirm in any order."}
            {status.validityHours != null ? ` Valid for ${status.validityHours} hours, while the confirmed content stays unchanged.` : " Valid while the confirmed content stays unchanged."}
        </Text>
        {contactBlocked &&
            <Text id={`${id}-sign-in-required`}>Sign in to give this confirmation with the verification this Template
                requires.</Text>}
        {status.attestations.length > 0 && <ul id={`${id}-confirmations`}
                                               aria-label={`Confirmations for ${status.prompt}`}>
            {status.attestations.map(assent => <li id={`${id}-confirmation-${assent.id}`}
                                                   key={assent.id}>
                {`${assent.attestedByCaller ? "You" : roleLabel(assent.partyRole)} ${assent.decision === InformationRequestAttestationDecision.ASSENTED ? "confirmed" : "refused"} at ${formatInformationRequestTime(assent.attestedAt)}.`}
            </li>)}
        </ul>}
    </>;
};

export default SubmissionAttestationPolicy;