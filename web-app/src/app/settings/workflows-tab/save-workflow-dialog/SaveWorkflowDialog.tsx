import {useState} from "react";
import {
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Divider,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {useStyles} from './SaveWorkflowDialogStyles.tsx';
import TagList from '../../../components/TagList.tsx';
import {WorkflowDesignerState, WorkflowTriggerEventDto} from "../../../models/models.tsx";
import {formatTriggerName, STEP_TYPE_LABELS} from "../workflowUtils.ts";
import {
    completeStepUpWithOtp,
    initiateStepUp,
    regenerateStepUpOtp,
    StepUpInitiateResponse,
} from "../../../../services/authApi.ts";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../../utils/apiErrorUtils.ts";
import StepUpVerification, {STEP_UP_DIALOG_TITLE} from "../../../components/step-up/StepUpVerification.tsx";

type Phase = 'review' | 'verify-otp' | 'verify-external';

interface Props {
    open: boolean;
    onClose: () => void;
    onConfirm: () => Promise<void>;
    isEdit: boolean;
    state: WorkflowDesignerState;
    triggers: WorkflowTriggerEventDto[];
}

const WORKFLOW_SAVE_ACTION = "WORKFLOW_DEFINITION_SAVE";

const SaveWorkflowDialog = ({open, onClose, onConfirm, isEdit, state, triggers}: Props) => {
    const styles = useStyles();

    const [phase, setPhase] = useState<Phase>('review');
    const [stepUpSession, setStepUpSession] = useState<StepUpInitiateResponse | null>(null);

    const [initiating, setInitiating] = useState(false);
    const [otp, setOtp] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [resending, setResending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [info, setInfo] = useState<string | null>(null);

    const reset = () => {
        setPhase('review');
        setStepUpSession(null);
        setInitiating(false);
        setOtp("");
        setSubmitting(false);
        setResending(false);
        setError(null);
        setInfo(null);
    };

    const handleClose = () => {
        if (submitting || initiating) return;
        reset();
        onClose();
    };

    const handleProceedToVerify = async () => {
        setInitiating(true);
        setError(null);
        try {
            const returnTo = `${window.location.pathname}${window.location.search}`;
            const session = await initiateStepUp(returnTo, WORKFLOW_SAVE_ACTION);
            setStepUpSession(session);
            setPhase(session.method === 'INTERNAL_EMAIL_OTP' ? 'verify-otp' : 'verify-external');
        } catch (e: unknown) {
            setError(getOtpFriendlyMessage(normalizeApiError(e, "Could not initiate verification. Please try again.")));
        } finally {
            setInitiating(false);
        }
    };

    const handleSubmitOtp = async () => {
        if (!stepUpSession?.mfaSessionId || submitting) return;
        if (!otp.trim()) {
            setError("Verification code is required.");
            return;
        }
        setSubmitting(true);
        setError(null);
        setInfo(null);
        try {
            const result = await completeStepUpWithOtp(stepUpSession.mfaSessionId, otp.trim());
            if (!result?.fresh) {
                setError("Verification failed. Please try again.");
                return;
            }
            await onConfirm();
        } catch (e: unknown) {
            setError(getOtpFriendlyMessage(normalizeApiError(
                e,
                isEdit ? "Failed to update workflow." : "Failed to save workflow.",
            )));
        } finally {
            setSubmitting(false);
        }
    };

    const handleResendOtp = async () => {
        if (!stepUpSession?.mfaSessionId || resending) return;
        setResending(true);
        setError(null);
        try {
            const result = await regenerateStepUpOtp(stepUpSession.mfaSessionId);
            setInfo(result?.message || "A new verification code has been sent.");
        } catch (e: unknown) {
            setError(getOtpFriendlyMessage(normalizeApiError(e, "Could not resend code.")));
        } finally {
            setResending(false);
        }
    };

    const handleContinueExternal = async () => {
        if (!stepUpSession?.authorizeUrl || submitting) return;
        setSubmitting(true);
        setError(null);
        try {
            window.location.assign(stepUpSession.authorizeUrl);
        } catch (e: unknown) {
            setError(getOtpFriendlyMessage(normalizeApiError(e, "Could not start external re-login.")));
            setSubmitting(false);
        }
    };

    const selectedTrigger = triggers.find(t => t.eventName === state.triggerEvent);
    const isBusy = submitting || initiating || resending;

    const title = phase === 'review'
        ? (isEdit ? "Review & Update Workflow" : "Review & Save Workflow")
        : STEP_UP_DIALOG_TITLE;

    return (
        <Dialog open={open} onOpenChange={(_, d) => { if (!d.open) handleClose(); }}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>{title}</DialogTitle>
                    <DialogContent>
                        <div className={styles.contentWrapper}>

                            {phase === 'review' && error && (
                                <MessageBar intent="error">
                                    <MessageBarBody>{error}</MessageBarBody>
                                </MessageBar>
                            )}

                            {phase === 'review' && (
                                <>
                                    <Text
                                        size={200}
                                        className={styles.mutedText}
                                    >
                                        {isEdit
                                            ? "Review your changes before updating the workflow."
                                            : "Review your workflow before saving."}
                                    </Text>
                                    <div className={styles.reviewSection}>
                                        <div className={styles.reviewRow}>
                                            <Text className={styles.reviewLabel}>Name</Text>
                                            <Text weight="semibold">{state.name}</Text>
                                        </div>
                                        <div className={styles.reviewRow}>
                                            <Text className={styles.reviewLabel}>Trigger Event</Text>
                                            <Text>{formatTriggerName(state.triggerEvent)}</Text>
                                            {selectedTrigger?.description && (
                                                <Text
                                                    size={200}
                                                    className={styles.mutedText}
                                                >
                                                    {selectedTrigger.description}
                                                </Text>
                                            )}
                                        </div>
                                        {state.summary && (
                                            <div className={styles.reviewRow}>
                                                <Text className={styles.reviewLabel}>Summary</Text>
                                                <Text>{state.summary}</Text>
                                            </div>
                                        )}
                                        {state.generalTags.length > 0 && (
                                            <div className={styles.reviewRow}>
                                                <Text className={styles.reviewLabel}>Tags</Text>
                                                <TagList tags={state.generalTags}/>
                                            </div>
                                        )}
                                        <div className={styles.reviewRow}>
                                            <Text className={styles.reviewLabel}>Status</Text>
                                            <Badge
                                                color={state.isActive ? "success" : "informative"}
                                                appearance="filled"
                                                size="small"
                                            >
                                                {state.isActive ? "Active" : "Inactive"}
                                            </Badge>
                                        </div>
                                        <Divider/>
                                        <div className={styles.reviewRow}>
                                            <Text className={styles.reviewLabel}>
                                                Steps ({state.steps.length})
                                            </Text>
                                            {state.steps.length === 0 ? (
                                                <Text
                                                    size={200}
                                                    className={styles.mutedText}
                                                >
                                                    No steps configured.
                                                </Text>
                                            ) : (
                                                <div className={styles.stepList}>
                                                    {state.steps.map((step, i) => (
                                                        <div key={i} className={styles.stepItem}>
                                                            <span className={styles.stepNumber}>{i + 1}</span>
                                                            <Text size={300}>
                                                                {STEP_TYPE_LABELS[step.type] ?? step.type}
                                                                {step.assignees.length > 0 && (
                                                                    <span className={styles.mutedSpan}>
                                                                        {"  -  "}{step.assignees.length} assignee{step.assignees.length !== 1 ? "s" : ""}
                                                                    </span>
                                                                )}
                                                            </Text>
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </>
                            )}

                            {(phase === 'verify-otp' || phase === 'verify-external') && (
                                <StepUpVerification
                                    method={phase === 'verify-otp' ? "INTERNAL_EMAIL_OTP" : "EXTERNAL_RELOGIN"}
                                    actionLabel={<>{isEdit ? "update" : "save"} this workflow</>}
                                    provider={stepUpSession?.provider}
                                    error={error}
                                    otp={otp}
                                    onOtpChange={setOtp}
                                    onSubmitOtp={handleSubmitOtp}
                                    submitting={submitting}
                                    info={info}
                                    onResend={handleResendOtp}
                                    resending={resending}
                                    resendDisabled={isBusy}
                                    otpInputId="input-workflow-otp"
                                    resendButtonId="button-workflow-resend-code"
                                />
                            )}
                        </div>
                    </DialogContent>

                    <DialogActions>
                        {phase === 'review' && (
                            <>
                                <Button
                                    id={"button-workflow-save-confirm"}
                                    appearance="primary"
                                    shape="circular"
                                    onClick={handleProceedToVerify}
                                    disabled={isBusy}
                                >
                                    {initiating ? <><Spinner size="tiny"/> Verifying…</> : (isEdit ? "Confirm & Update" : "Confirm & Save")}
                                </Button>
                                <Button
                                    id={"button-workflow-save-back"}
                                    appearance="secondary"
                                    shape="circular"
                                    onClick={handleClose}
                                    disabled={isBusy}
                                >
                                    Back to editing
                                </Button>
                            </>
                        )}

                        {phase === 'verify-otp' && (
                            <>
                                <Button
                                    id={"button-workflow-verify-continue"}
                                    appearance="primary"
                                    shape="circular"
                                    onClick={handleSubmitOtp}
                                    disabled={isBusy}
                                >
                                    {submitting ? <><Spinner size="tiny"/> {isEdit ? "Updating…" : "Saving…"}</> : "Verify & continue"}
                                </Button>
                                <Button
                                    id={"button-workflow-verify-back"}
                                    appearance="secondary"
                                    shape="circular"
                                    onClick={() => { setPhase('review'); setError(null); }}
                                    disabled={isBusy}
                                >
                                    Back to review
                                </Button>
                            </>
                        )}

                        {phase === 'verify-external' && (
                            <>
                                <Button
                                    id={"button-workflow-continue-external"}
                                    appearance="primary"
                                    shape="circular"
                                    onClick={handleContinueExternal}
                                    disabled={isBusy}
                                >
                                    {submitting ? <><Spinner size="tiny"/> Redirecting…</> : `Continue to ${stepUpSession?.provider || "provider"}`}
                                </Button>
                                <Button
                                    id={"button-workflow-external-back"}
                                    appearance="secondary"
                                    shape="circular"
                                    onClick={() => { setPhase('review'); setError(null); }}
                                    disabled={isBusy}
                                >
                                    Back to review
                                </Button>
                            </>
                        )}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SaveWorkflowDialog;
