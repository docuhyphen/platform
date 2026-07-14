import {Button, DialogActions, Spinner} from "@fluentui/react-components";

interface MfaDialogActionsProps
{
    configured: boolean;
    enrolling: boolean;
    busy: boolean;
    codeComplete: boolean;
    onPrimary: () => void;
    onCancel: () => void;
}

const MfaDialogActions = ({
    configured,
    enrolling,
    busy,
    codeComplete,
    onPrimary,
    onCancel,
}: MfaDialogActionsProps) =>
{
    return <DialogActions
        id={"mfa-settings-dialog-actions"}
        position={"end"}>
        <Button
            id={enrolling ? "mfa-verify-enrollment-button" : "mfa-primary-action-button"}
            appearance={"primary"}
            shape={"circular"}
            disabled={busy || (enrolling && !codeComplete)}
            onClick={onPrimary}>
            {busy && <Spinner
                id={"mfa-action-spinner"}
                size={"tiny"}
            />} {enrolling
                ? 'Verify and enable'
                : (configured ? 'Save' : 'Set up')}
        </Button>
        <Button
            id={"mfa-cancel-button"}
            appearance={"secondary"}
            shape={"circular"}
            disabled={busy}
            onClick={onCancel}>
            Cancel
        </Button>
    </DialogActions>;
};

export default MfaDialogActions;
