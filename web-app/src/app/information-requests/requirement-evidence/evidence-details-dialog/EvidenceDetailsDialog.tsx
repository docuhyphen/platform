import {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Text,
} from "@fluentui/react-components";
import {
    CapturedEvidenceDetail,
    EvidenceDetails,
    evidenceDetailsComplete,
    evidenceDetailsProblem,
    submittedDetails,
} from "../evidenceDetails.ts";
import EvidenceDetailInput from "./EvidenceDetailInput.tsx";
import {useEvidenceDetailsDialogStyles} from "./EvidenceDetailsDialogStyles.tsx";

interface Props {
    id: string;
    fileName: string;
    actionLabel: string;
    captured: CapturedEvidenceDetail[];
    initial: EvidenceDetails;
    onConfirm: (details: EvidenceDetails) => void;
    onDismiss: () => void;
}

const EvidenceDetailsDialog = ({id, fileName, actionLabel, captured, initial, onConfirm, onDismiss}: Props) => {
    const styles = useEvidenceDetailsDialogStyles();
    const [details, setDetails] = useState<EvidenceDetails>(initial);
    const problem = evidenceDetailsProblem(captured, details);

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => {
                    if (!data.open) onDismiss();
                }}>
            <DialogSurface id={id}
                           className={styles.surface}>
                <DialogBody id={`${id}-body`}>
                    <DialogTitle id={`${id}-title`}>File details</DialogTitle>
                    <DialogContent id={`${id}-content`}
                                   className={styles.content}>
                        <Text id={`${id}-summary`}>
                            State what {fileName} shows. Required details are marked.
                        </Text>
                        <div id={`${id}-fields`}
                             className={styles.fields}>
                            {captured.map(detail => (
                                <EvidenceDetailInput key={detail.key}
                                                     id={`${id}-${detail.elementSuffix}`}
                                                     detail={detail}
                                                     value={details[detail.key] ?? ""}
                                                     onChange={value => setDetails(previous => ({
                                                         ...previous,
                                                         [detail.key]: value
                                                     }))}/>
                            ))}
                        </div>
                        {problem && (
                            <Text id={`${id}-problem`}
                                  role={"alert"}
                                  className={styles.problem}>
                                {problem}
                            </Text>
                        )}
                    </DialogContent>
                    <DialogActions id={`${id}-actions`}
                                   className={styles.actions}>
                        <Button id={`${id}-confirm`}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={!evidenceDetailsComplete(captured, details)}
                                onClick={() => onConfirm(submittedDetails(captured, details))}>
                            {actionLabel}
                        </Button>
                        <Button id={`${id}-cancel`}
                                appearance={"secondary"}
                                shape={"circular"}
                                onClick={onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default EvidenceDetailsDialog;
