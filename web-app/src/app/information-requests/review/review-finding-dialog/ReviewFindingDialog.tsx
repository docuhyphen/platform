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
    InformationRequestReviewItemDto,
    InformationRequestReviewVisibility,
    RecordInformationRequestReviewFindingRequest,
} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {correctionScopeLabels, findingSeverityLabels, visibilityLabels} from "../reviewLabels.ts";
import ReviewFindingChoice from "./ReviewFindingChoice.tsx";
import {useReviewFindingDialogStyles} from "./ReviewFindingDialogStyles.tsx";

interface Props
{
    item: InformationRequestReviewItemDto;
    busy: boolean;
    onConfirm: (request: RecordInformationRequestReviewFindingRequest) => void;
    onDismiss: () => void;
}

const REASON_PATTERN = /^[a-z0-9][a-z0-9._-]{0,127}$/;

const ReviewFindingDialog = ({item, busy, onConfirm, onDismiss}: Props) =>
{
    const styles = useReviewFindingDialogStyles();
    const [reasonCode, setReasonCode] = useState("");
    const [narrative, setNarrative] = useState("");
    const [severity, setSeverity] = useState(InformationRequestFindingSeverity.MAJOR);
    const [visibility, setVisibility] = useState(InformationRequestReviewVisibility.RESPONDENT_VISIBLE);
    const [scope, setScope] = useState(InformationRequestFindingCorrectionScope.RESPONSE);
    const [evidenceVersionId, setEvidenceVersionId] = useState(item.evidence[0]?.evidenceVersionId ?? "");
    const normalizedReason = reasonCode.trim().toLowerCase();
    const reasonValid = REASON_PATTERN.test(normalizedReason);
    const fileScoped = scope === InformationRequestFindingCorrectionScope.EVIDENCE_VERSION;
    const ready = reasonValid && narrative.trim().length > 0 && (!fileScoped || evidenceVersionId !== "");

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open) onDismiss(); }}>
            <DialogSurface id={"information-request-review-finding-dialog"}
                           className={styles.surface}>
                <DialogBody>
                    <DialogTitle id={"information-request-review-finding-dialog-title"}>
                        {`Finding for ${humanizedKey(item.requirementKey)}`}
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
                        <ReviewFindingChoice id={"information-request-review-finding-severity"}
                                             label={"Severity"}
                                             options={Object.values(InformationRequestFindingSeverity)}
                                             labelOf={value => findingSeverityLabels[value]}
                                             value={severity}
                                             disabled={busy}
                                             onChange={value => setSeverity(value)}/>
                        <ReviewFindingChoice id={"information-request-review-finding-visibility"}
                                             label={"Who can see it"}
                                             options={Object.values(InformationRequestReviewVisibility)}
                                             labelOf={value => visibilityLabels[value]}
                                             value={visibility}
                                             disabled={busy}
                                             onChange={value => setVisibility(value)}/>
                        <ReviewFindingChoice id={"information-request-review-finding-scope"}
                                             label={"What the respondent may change"}
                                             options={Object.values(InformationRequestFindingCorrectionScope)}
                                             labelOf={value => correctionScopeLabels[value]}
                                             value={scope}
                                             disabled={busy}
                                             onChange={value => setScope(value)}/>
                        {fileScoped && (
                            <ReviewFindingChoice id={"information-request-review-finding-file"}
                                                 label={"File to replace"}
                                                 options={item.evidence.map(file => file.evidenceVersionId)}
                                                 labelOf={value => `File version ${item.evidence.find(file => file.evidenceVersionId === value)?.versionNumber ?? ""}`}
                                                 value={evidenceVersionId}
                                                 disabled={busy}
                                                 onChange={value => setEvidenceVersionId(value)}/>
                        )}
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
                                    evidenceVersionId: fileScoped ? evidenceVersionId : undefined,
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
