import {Button} from "@fluentui/react-components";
import {useSubmissionAttestationActionsStyles} from "./SubmissionAttestationActionsStyles.tsx";

interface Props {
    id: string;
    label: string;
    busy: boolean;
    refusing: boolean;
    canConfirm: boolean;
    canRefuse: boolean;
    onConfirm: () => void;
    onRefuse: () => void;
}

const SubmissionAttestationActions = ({
                                          id,
                                          label,
                                          busy,
                                          refusing,
                                          canConfirm,
                                          canRefuse,
                                          onConfirm,
                                          onRefuse
                                      }: Props) => {
    const styles = useSubmissionAttestationActionsStyles();
    return <div id={`${id}-actions`}
                className={styles.actions}>
        <Button id={`${id}-confirm`}
                appearance={refusing ? "secondary" : "primary"}
                shape={"circular"}
                aria-label={refusing ? `Keep confirmation for ${label}` : `Confirm ${label}`}
                disabled={busy || (!refusing && !canConfirm)}
                onClick={onConfirm}>
            {refusing ? "Keep" : "Confirm"}
        </Button>
        <Button id={`${id}-refuse`}
                appearance={refusing ? "primary" : "secondary"}
                shape={"circular"}
                aria-label={refusing ? `Refuse ${label}` : `Refuse to confirm ${label}`}
                disabled={busy || !canRefuse}
                onClick={onRefuse}>
            {refusing ? "Refuse" : "Refuse to confirm"}
        </Button>
    </div>;
};

export default SubmissionAttestationActions;