import {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Spinner,
} from "@fluentui/react-components";
import {setupPassword} from "../../../../services/authApi.ts";
import {ResponseError} from "../../../models/models.tsx";
import {useSetupPasswordDialogStyles} from "./SetupPasswordDialogStyles.tsx";
import SetupPasswordSuccess from "./SetupPasswordSuccess.tsx";
import SetupPasswordFields from "./SetupPasswordFields.tsx";

interface SetupPasswordDialogProps
{
    open: boolean;
    onClose: () => void;
}

const SetupPasswordDialog = ({open, onClose}: SetupPasswordDialogProps) =>
{
    const styles = useSetupPasswordDialogStyles();
    const [password, setPassword] = useState("");
    const [confirmationPassword, setConfirmationPassword] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string>();
    const [completed, setCompleted] = useState(false);

    const close = () =>
    {
        if (saving || completed) return;
        setPassword("");
        setConfirmationPassword("");
        setError(undefined);
        onClose();
    };

    const submit = async () =>
    {
        if (!password || !confirmationPassword)
        {
            setError("Password and confirmation are required.");
            return;
        }
        if (password !== confirmationPassword)
        {
            setError("Passwords do not match.");
            return;
        }

        setSaving(true);
        setError(undefined);
        try
        {
            await setupPassword({password, confirmationPassword});
            setPassword("");
            setConfirmationPassword("");
            setCompleted(true);
        }
        catch (requestError)
        {
            const responseError = requestError as ResponseError;
            setError(responseError?.errorMessage || "Failed to set up email and password sign-in.");
        }
        finally
        {
            setSaving(false);
        }
    };

    const continueToSignIn = () =>
    {
        window.dispatchEvent(new CustomEvent("auth-sign-in-required"));
    };

    return (
        <Dialog
            open={open}
            onOpenChange={(_, data) => !data.open && close()}>
            <DialogSurface id={"setup-password-dialog-surface"}>
                <DialogBody id={"setup-password-dialog-body"}>
                    <DialogTitle id={"setup-password-dialog-title"}>
                        {completed ? "Email and password sign-in added" : "Add email and password sign-in"}
                    </DialogTitle>
                    <DialogContent
                        id={"setup-password-dialog-content"}
                        className={styles.content}>
                        {completed
                            ? <SetupPasswordSuccess/>
                            : <SetupPasswordFields
                                password={password}
                                confirmationPassword={confirmationPassword}
                                saving={saving}
                                error={error}
                                onPasswordChange={setPassword}
                                onConfirmationPasswordChange={setConfirmationPassword}
                            />}
                    </DialogContent>
                    <DialogActions id={"setup-password-dialog-actions"}>
                        {completed ? <Button
                            id={"setup-password-continue-sign-in-btn"}
                            appearance={"primary"}
                            shape={"circular"}
                            onClick={continueToSignIn}>
                            Continue to sign in
                        </Button> : <>
                            <Button
                                id={"setup-password-submit-btn"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={saving}
                                onClick={submit}>
                                {saving ? <><Spinner size={"tiny"}/> Adding password</> : "Add password"}
                            </Button>
                            <Button
                                id={"setup-password-cancel-btn"}
                                appearance={"secondary"}
                                shape={"circular"}
                                disabled={saving}
                                onClick={close}>
                                Cancel
                            </Button>
                        </>}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SetupPasswordDialog;
