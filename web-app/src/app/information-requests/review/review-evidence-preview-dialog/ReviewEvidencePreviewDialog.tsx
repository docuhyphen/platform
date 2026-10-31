import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle
} from "@fluentui/react-components";
import {PreviewedReviewEvidence} from "../model/PreviewedReviewEvidence.ts";
import {useReviewEvidencePreviewDialogStyles} from "./ReviewEvidencePreviewDialogStyles.tsx";

interface Props {
    id: string;
    preview: PreviewedReviewEvidence;
    onClose: () => void;
}

const ReviewEvidencePreviewDialog = ({id, preview, onClose}: Props) => {
    const styles = useReviewEvidencePreviewDialogStyles();
    return <Dialog open={true}
                   onOpenChange={(_, data) => {
                       if (!data.open) onClose();
                   }}>
        <DialogSurface id={id}
                       aria-labelledby={`${id}-title`}
                       className={styles.surface}>
            <DialogBody id={`${id}-body`}>
                <DialogTitle id={`${id}-title`}>{`Preview ${preview.fileName}`}</DialogTitle>
                <DialogContent id={`${id}-content`}>
                    <iframe id={`${id}-frame`}
                            title={`Preview of ${preview.fileName}`}
                            src={preview.url}
                            sandbox={"allow-same-origin"}
                            referrerPolicy={"no-referrer"}
                            className={styles.frame}/>
                </DialogContent>
                <DialogActions id={`${id}-actions`}
                               className={styles.actions}>
                    <Button id={`${id}-close`}
                            shape={"circular"}
                            appearance={"primary"}
                            aria-label={"Close preview"}
                            onClick={onClose}>Close</Button>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>;
};

export default ReviewEvidencePreviewDialog;