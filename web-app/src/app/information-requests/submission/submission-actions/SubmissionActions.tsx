import {Button, Text} from "@fluentui/react-components";
import {PendingWorkBlocker} from "../../shared/respondentPendingWork.ts";
import {pendingWorkLabels} from "../submissionLabels.ts";
import {useSubmissionActionsStyles} from "./SubmissionActionsStyles.tsx";

interface Props {
    label: string;
    disabled: boolean;
    blocker: PendingWorkBlocker | null;
    unavailableReason: string | null;
    onSubmit: () => void;
}

const PENDING_ID = "information-request-submission-pending";

const SubmissionActions = ({label, disabled, blocker, unavailableReason, onSubmit}: Props) => {
    const styles = useSubmissionActionsStyles();

    return (
        <div id={"information-request-submission-actions"}
             className={styles.actions}>
            <Button id={"information-request-submission-submit"}
                    appearance={"primary"}
                    shape={"circular"}
                    disabled={disabled || blocker !== null}
                    aria-describedby={blocker ? PENDING_ID : undefined}
                    onClick={onSubmit}>
                {label}
            </Button>
            {blocker && (
                <Text id={PENDING_ID}
                      role={"status"}
                      className={styles.pending}>
                    {pendingWorkLabels[blocker]}
                </Text>
            )}
            {unavailableReason && (
                <Text id={"information-request-submission-unavailable"}
                      className={styles.detail}>
                    {unavailableReason}
                </Text>
            )}
        </div>
    );
};

export default SubmissionActions;
