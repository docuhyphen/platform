import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
} from "@fluentui/react-components";
import {useSaveWorkflowDialogStyles} from './SaveWorkflowDialogStyles.tsx';
import {WorkflowDesignerState, WorkflowTriggerEventDto} from "../../../models/models.tsx";
import StepUpVerification, {STEP_UP_DIALOG_TITLE} from "../../../components/step-up/StepUpVerification.tsx";
import SaveWorkflowDialogFooter from "./save-workflow-dialog-footer/SaveWorkflowDialogFooter.tsx";
import SaveWorkflowWarnings from "./save-workflow-warnings/SaveWorkflowWarnings.tsx";
import WorkflowReviewSummary from "./workflow-review-summary/WorkflowReviewSummary.tsx";
import {useSaveWorkflowStepUp} from "./useSaveWorkflowStepUp.ts";

interface Props
{
    open: boolean;
    onClose: () => void;
    onConfirm: () => Promise<void>;
    isEdit: boolean;
    state: WorkflowDesignerState;
    triggers: WorkflowTriggerEventDto[];
    warnings?: string[];
}

const SaveWorkflowDialog = ({open, onClose, onConfirm, isEdit, state, triggers, warnings = []}: Props) =>
{
    const styles = useSaveWorkflowDialogStyles();
    const stepUp = useSaveWorkflowStepUp({isEdit, onConfirm});
    const {phase, stepUpSession, error, isBusy} = stepUp;

    const handleClose = () =>
    {
        if (stepUp.submitting || stepUp.initiating) return;
        stepUp.reset();
        onClose();
    };

    const selectedTrigger = triggers.find(t => t.eventName === state.triggerEvent);
    const title = phase === 'review'
        ? (isEdit ? "Review & Update Workflow" : "Review & Save Workflow")
        : STEP_UP_DIALOG_TITLE;

    return (
        <Dialog open={open}
                onOpenChange={(_, d) => { if (!d.open) handleClose(); }}>
            <DialogSurface id={"save-workflow-dialog-dialog-surface-1"}>
                <DialogBody id={"save-workflow-dialog-dialog-body-1"}>
                    <DialogTitle id={"save-workflow-dialog-dialog-title-1"}>{title}</DialogTitle>
                    <DialogContent id={"save-workflow-dialog-dialog-content-1"}>
                        <div id={"save-workflow-dialog-div-1"}
                             className={styles.contentWrapper}>

                            {phase === 'review' && error && (
                                <MessageBar id={"save-workflow-dialog-message-bar-1"}
                                            intent="error">
                                    <MessageBarBody id={"save-workflow-dialog-message-bar-body-1"}>{error}</MessageBarBody>
                                </MessageBar>
                            )}

                            {phase === 'review' && warnings.length > 0 && (
                                <SaveWorkflowWarnings warnings={warnings}/>
                            )}

                            {phase === 'review' && (
                                <WorkflowReviewSummary isEdit={isEdit}
                                                       state={state}
                                                       selectedTrigger={selectedTrigger}/>
                            )}

                            {(phase === 'verify-otp' || phase === 'verify-external') && (
                                <StepUpVerification
                                    method={phase === 'verify-otp' ? "INTERNAL_EMAIL_OTP" : "EXTERNAL_RELOGIN"}
                                    actionLabel={<>{isEdit ? "update" : "save"} this workflow</>}
                                    provider={stepUpSession?.provider}
                                    message={stepUpSession?.message}
                                    error={error ?? stepUp.cooldownMessage}
                                    otp={stepUp.otp}
                                    onOtpChange={stepUp.setOtp}
                                    onSubmitOtp={stepUp.submitOtp}
                                    submitting={stepUp.submitting}
                                    info={stepUp.info}
                                    onResend={stepUpSession?.mfaType === 'EMAIL' || stepUpSession?.emailFallbackEnabled
                                        ? stepUp.resendOtp
                                        : undefined}
                                    resending={stepUp.resending}
                                    resendDisabled={isBusy || stepUp.cooldownRemaining > 0}
                                    otpInputId="input-workflow-otp"
                                    resendButtonId="button-workflow-resend-code"
                                    resendLabel={stepUpSession?.mfaType === 'EMAIL' ? 'Resend code' : 'Use email fallback'}
                                />
                            )}
                        </div>
                    </DialogContent>

                    <SaveWorkflowDialogFooter
                        phase={phase}
                        isEdit={isEdit}
                        initiating={stepUp.initiating}
                        submitting={stepUp.submitting}
                        isBusy={isBusy}
                        provider={stepUpSession?.provider}
                        onProceed={stepUp.proceedToVerify}
                        onSubmitOtp={stepUp.submitOtp}
                        onContinueExternal={stepUp.continueExternal}
                        onClose={handleClose}
                        onReturnToReview={stepUp.returnToReview}
                    />
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SaveWorkflowDialog;
