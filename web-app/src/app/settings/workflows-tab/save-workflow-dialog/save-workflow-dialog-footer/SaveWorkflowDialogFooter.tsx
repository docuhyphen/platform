import {ReactNode} from "react";
import {Button, DialogActions, Spinner} from "@fluentui/react-components";
import {identityProviderDisplayName} from "../../../../authorization/identityProviderDisplayName.ts";
import {SaveWorkflowPhase} from "../useSaveWorkflowStepUp.ts";

interface FooterActions
{
    primaryId: string;
    primaryLabel: ReactNode;
    onPrimary: () => void;
    backId: string;
    backLabel: string;
    onBack: () => void;
}

interface Props
{
    phase: SaveWorkflowPhase;
    isEdit: boolean;
    initiating: boolean;
    submitting: boolean;
    isBusy: boolean;
    provider?: string;
    onProceed: () => void;
    onSubmitOtp: () => void;
    onContinueExternal: () => void;
    onClose: () => void;
    onReturnToReview: () => void;
}

const SaveWorkflowDialogFooter = (props: Props) =>
{
    const {phase, isEdit, initiating, submitting, isBusy, provider} = props;

    const footerActionsByPhase: Record<SaveWorkflowPhase, FooterActions> = {
        'review': {
            primaryId: "button-workflow-save-confirm",
            primaryLabel: initiating
                ? <><Spinner id={"save-workflow-dialog-spinner-1"}
                             size="tiny"/> Verifying…</>
                : (isEdit ? "Confirm & Update" : "Confirm & Save"),
            onPrimary: props.onProceed,
            backId: "button-workflow-save-back",
            backLabel: "Back to editing",
            onBack: props.onClose,
        },
        'verify-otp': {
            primaryId: "button-workflow-verify-continue",
            primaryLabel: submitting
                ? <><Spinner id={"save-workflow-dialog-spinner-2"}
                             size="tiny"/> {isEdit ? "Updating…" : "Saving…"}</>
                : "Verify & continue",
            onPrimary: props.onSubmitOtp,
            backId: "button-workflow-verify-back",
            backLabel: "Back to review",
            onBack: props.onReturnToReview,
        },
        'verify-external': {
            primaryId: "button-workflow-continue-external",
            primaryLabel: submitting
                ? <><Spinner id={"save-workflow-dialog-spinner-3"}
                             size="tiny"/> Redirecting...</>
                : `Continue to ${identityProviderDisplayName(provider)}`,
            onPrimary: props.onContinueExternal,
            backId: "button-workflow-external-back",
            backLabel: "Back to review",
            onBack: props.onReturnToReview,
        },
    };
    const footerActions = footerActionsByPhase[phase];

    return (
        <DialogActions id={"save-workflow-dialog-dialog-actions-1"}>
            <Button
                id={footerActions.primaryId}
                appearance="primary"
                shape="circular"
                onClick={footerActions.onPrimary}
                disabled={isBusy}
            >
                {footerActions.primaryLabel}
            </Button>
            <Button
                id={footerActions.backId}
                appearance="secondary"
                shape="circular"
                onClick={footerActions.onBack}
                disabled={isBusy}
            >
                {footerActions.backLabel}
            </Button>
        </DialogActions>
    );
};

export default SaveWorkflowDialogFooter;
