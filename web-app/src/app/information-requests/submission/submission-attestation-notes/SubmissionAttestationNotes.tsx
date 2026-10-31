import {Text} from "@fluentui/react-components";
import {
    InformationRequestAttestationDecision,
    InformationRequestAttestationStatusDto
} from "../../../models/models.tsx";
import {roleLabel} from "../submissionLabels.ts";
import {useSubmissionAttestationNotesStyles} from "./SubmissionAttestationNotesStyles.tsx";

interface Props
{
    id: string;
    status: InformationRequestAttestationStatusDto;
}

const SubmissionAttestationNotes = ({id, status}: Props) =>
{
    const styles = useSubmissionAttestationNotesStyles();
    const waiting = status.callerCanAttest && status.waitingForRoles.length > 0;

    return (
        <>
            {status.callerDecision && (
                <Text id={`${id}-caller-decision`}
                      className={styles.detail}>
                    {status.callerDecision === InformationRequestAttestationDecision.ASSENTED ? "You confirmed this." : "You refused this."}
                </Text>
            )}
            {waiting && (
                <Text id={`${id}-waiting`}
                      className={styles.detail}>
                    {`Waiting for ${status.waitingForRoles.map(roleLabel).join(", ")} to confirm first.`}
                </Text>
            )}
        </>
    );
};

export default SubmissionAttestationNotes;
