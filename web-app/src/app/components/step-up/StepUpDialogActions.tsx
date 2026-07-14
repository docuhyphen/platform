import {Button, DialogActions, Spinner} from "@fluentui/react-components";

interface StepUpDialogActionsProps
{
    otpFlow: boolean;
    submitting: boolean;
    provider?: string | null;
    onSubmitOtp: () => void;
    onContinueExternal: () => void;
    onCancel: () => void;
}

const StepUpDialogActions = ({
    otpFlow,
    submitting,
    provider,
    onSubmitOtp,
    onContinueExternal,
    onCancel,
}: StepUpDialogActionsProps) => <DialogActions id={"step-up-dialog-actions"}>
    <Button
        id={otpFlow ? "step-up-verify-btn" : "step-up-continue-external-btn"}
        appearance={"primary"}
        shape={"circular"}
        disabled={submitting}
        onClick={otpFlow ? onSubmitOtp : onContinueExternal}>
        {submitting && <Spinner
            id={"step-up-submit-spinner"}
            size={"tiny"}
        />}
        {otpFlow ? "Verify and continue" : `Continue to ${provider || "provider"}`}
    </Button>
    <Button
        id={"step-up-cancel-btn"}
        appearance={"secondary"}
        shape={"circular"}
        disabled={submitting}
        onClick={onCancel}>
        Cancel
    </Button>
</DialogActions>;

export default StepUpDialogActions;
