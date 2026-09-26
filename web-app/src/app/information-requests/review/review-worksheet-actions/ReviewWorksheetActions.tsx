import {Button, Text} from "@fluentui/react-components";
import {useReviewWorksheetActionsStyles} from "./ReviewWorksheetActionsStyles.tsx";

interface Props
{
    busy: boolean;
    changedCount: number;
    onSave: () => void;
    onRecord: () => void;
}

const ReviewWorksheetActions = ({busy, changedCount, onSave, onRecord}: Props) =>
{
    const styles = useReviewWorksheetActionsStyles();

    return (
        <div id={"information-request-review-worksheet-actions"}
             className={styles.actions}>
            <Text id={"information-request-review-worksheet-status"}
                  className={styles.status}>
                {changedCount > 0 ? `${changedCount} unsaved changes` : "Every change is saved"}
            </Text>
            <Button id={"information-request-review-save-worksheet"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={busy || changedCount === 0}
                    onClick={onSave}>
                Save worksheet
            </Button>
            <Button id={"information-request-review-record-decisions"}
                    appearance={"primary"}
                    shape={"circular"}
                    disabled={busy}
                    onClick={onRecord}>
                Record decisions
            </Button>
        </div>
    );
};

export default ReviewWorksheetActions;
