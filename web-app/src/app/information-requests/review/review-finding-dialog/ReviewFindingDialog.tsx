import {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Input,
    Textarea,
} from "@fluentui/react-components";
import {
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestRetestResult,
    InformationRequestReviewFindingDto,
    InformationRequestReviewItemDto,
    InformationRequestReviewVisibility,
    RecordInformationRequestReviewFindingRequest,
} from "../../../models/models.tsx";
import ReviewDialogFeedback from "../review-dialog-feedback/ReviewDialogFeedback.tsx";
import {ReviewMessage} from "../useReviewFeedback.ts";
import ReviewFindingChoice from "./ReviewFindingChoice.tsx";
import ReviewFindingClassificationFields from "./ReviewFindingClassificationFields.tsx";
import ReviewFindingRetestFields, {NOT_A_RETEST} from "./ReviewFindingRetestFields.tsx";
import {useReviewFindingDialogStyles} from "./ReviewFindingDialogStyles.tsx";

interface Props
{
    item: InformationRequestReviewItemDto;
    label: string;
    earlierFindings: InformationRequestReviewFindingDto[];
    busy: boolean;
    message?: ReviewMessage | null;
    onConfirm: (request: RecordInformationRequestReviewFindingRequest) => void;
    onDismiss: () => void;
}

const REASON_PATTERN = /^[a-z0-9][a-z0-9._-]{0,127}$/;

const ReviewFindingDialog = ({item, label, earlierFindings, busy, message, onConfirm, onDismiss}: Props) =>
{
    const styles = useReviewFindingDialogStyles();
    const [reasonCode, setReasonCode] = useState("");
    const [narrative, setNarrative] = useState("");
    const [severity, setSeverity] = useState(InformationRequestFindingSeverity.MAJOR);
    const [visibility, setVisibility] = useState(InformationRequestReviewVisibility.RESPONDENT_VISIBLE);
    const [scope, setScope] = useState(InformationRequestFindingCorrectionScope.RESPONSE);
    const [evidenceVersionId, setEvidenceVersionId] = useState("");
    const [retestsFindingId, setRetestsFindingId] = useState(NOT_A_RETEST);
    const [retestResult, setRetestResult] = useState(InformationRequestRetestResult.RESOLVED);
    const retest = retestsFindingId === NOT_A_RETEST ? {} : {retestsFindingId, retestResult};
    const normalizedReason = reasonCode.trim().toLowerCase();
    const reasonValid = REASON_PATTERN.test(normalizedReason);
    const fileScoped = scope === InformationRequestFindingCorrectionScope.EVIDENCE_VERSION;
    const ready = reasonValid && narrative.trim().length > 0 && (!fileScoped || evidenceVersionId !== "");

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open) onDismiss(); }}>
            <DialogSurface id={"information-request-review-finding-dialog"}
                           className={styles.surface}>
                <DialogBody id={"information-request-review-finding-dialog-body"}>
                    <DialogTitle id={"information-request-review-finding-dialog-title"}>
                        {`Finding for ${label}`}
                    </DialogTitle>
                    <DialogContent id={"information-request-review-finding-dialog-content"}
                                   className={styles.content}>
                        <Field id={"information-request-review-finding-reason-field"}
                               label={"Reason code"}
                               hint={"A short lowercase key, for example record.incomplete"}
                               validationState={reasonCode && !reasonValid ? "error" : "none"}>
                            <Input id={"information-request-review-finding-reason"}
                                   value={reasonCode}
                                   disabled={busy}
                                   onChange={(_, data) => setReasonCode(data.value)}/>
                        </Field>
                        <Field id={"information-request-review-finding-narrative-field"}
                               label={"What needs attention"}>
                            <Textarea id={"information-request-review-finding-narrative"}
                                      value={narrative}
                                      maxLength={4000}
                                      disabled={busy}
                                      onChange={(_, data) => setNarrative(data.value)}/>
                        </Field>
                        <ReviewFindingClassificationFields severity={severity}
                                                           visibility={visibility}
                                                           scope={scope}
                                                           disabled={busy}
                                                           onSeverityChange={setSeverity}
                                                           onVisibilityChange={setVisibility}
                                                           onScopeChange={setScope}/>
                        {item.evidence.length > 0 && (
                            <ReviewFindingChoice id={"information-request-review-finding-file"}
                                                 label={fileScoped ? "File to replace" : "File (optional)"}
                                                 options={["", ...item.evidence.map(file => file.evidenceVersionId)]}
                                                 labelOf={value => {
                                                     const file = item.evidence.find(candidate => candidate.evidenceVersionId === value);
                                                     return file ? `${file.declaredFileName ?? "File"}, version ${file.versionNumber}` : "No exact file";
                                                 }}
                                                 value={evidenceVersionId}
                                                 disabled={busy}
                                                 onChange={value => setEvidenceVersionId(value)}/>
                        )}
                        <ReviewDialogFeedback id={"information-request-review-finding-refusal"}
                                              message={message}/>
                        <ReviewFindingRetestFields earlierFindings={earlierFindings}
                                                   retestsFindingId={retestsFindingId}
                                                   retestResult={retestResult}
                                                   disabled={busy}
                                                   onRetestChange={setRetestsFindingId}
                                                   onResultChange={setRetestResult}/>
                    </DialogContent>
                    <DialogActions id={"information-request-review-finding-dialog-actions"}
                                   className={styles.actions}>
                        <Button id={"information-request-review-finding-confirm"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || !ready}
                                onClick={() => onConfirm({
                                    submissionItemId: item.submissionItemId,
                                    reasonCode: normalizedReason,
                                    narrative: narrative.trim(),
                                    severity,
                                    visibility,
                                    correctionScope: scope,
                                    evidenceVersionId: evidenceVersionId || undefined,
                                    ...retest,
                                })}>
                            Record finding
                        </Button>
                        <Button id={"information-request-review-finding-cancel"}
                                appearance={"secondary"}
                                shape={"circular"}
                                disabled={busy}
                                onClick={onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ReviewFindingDialog;
