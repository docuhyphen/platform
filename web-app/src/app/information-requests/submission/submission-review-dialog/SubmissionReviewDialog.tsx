import {Text} from "@fluentui/react-components";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {SubmissionReviewItem} from "../submissionReview.ts";
import {useSubmissionReviewDialogStyles} from "./SubmissionReviewDialogStyles.tsx";

interface Props
{
    items: SubmissionReviewItem[];
    busy: boolean;
    onConfirm: () => void;
    onDismiss: () => void;
}

const SubmissionReviewDialog = ({items, busy, onConfirm, onDismiss}: Props) =>
{
    const styles = useSubmissionReviewDialogStyles();

    return (
        <EditorDialog id={"information-request-submission-review"}
                      title={"Review before submitting"}
                      confirmLabel={"Submit"}
                      dismissLabel={"Keep editing"}
                      busy={busy}
                      wide={true}
                      onConfirm={onConfirm}
                      onDismiss={onDismiss}>
            <Text id={"information-request-submission-review-explanation"}>
                Check your answers. After you submit, they are locked unless a reviewer asks for a correction.
            </Text>
            <ul id={"information-request-submission-review-items"}
                className={styles.list}>
                {items.map(item => (
                    <li key={item.requirementId}
                        id={`information-request-submission-review-${item.requirementId}`}
                        className={styles.item}>
                        <Text weight={"semibold"}>{item.label}</Text>
                        <Text className={styles.answer}>{item.answer}</Text>
                    </li>
                ))}
            </ul>
        </EditorDialog>
    );
};

export default SubmissionReviewDialog;
