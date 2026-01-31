import React, {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useNavigate} from "react-router-dom";
import {completeAppUserEmailUpdate, initiateAppUserEmailUpdate} from "../../../services/appUserApi";
import {useAppUserEmailUpdateDialogStyles} from "./AppUserEmailUpdateDialogStyles.tsx";

interface AppUserEmailUpdateDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    currentEmail?: string;
}

const AppUserEmailUpdateDialog: React.FC<AppUserEmailUpdateDialogProps> = (
    {
        isOpen,
        onDismiss,
        currentEmail
    }) =>
{
    const styles = useAppUserEmailUpdateDialogStyles()
    const {token, setToken} = useAuth();
    const navigate = useNavigate();
    const [email, setEmail] = useState<string>("");
    const [processing, setProcessing] = useState(false);
    const [updateInitiated, setUpdateInitiated] = useState(false);
    const [verificationCode, setVerificationCode] = useState("");
    const [error, setError] = useState<string | null>(null);

    const onEmailChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setEmail(e.target.value);
    };

    const onVerificationCodeChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setVerificationCode(e.target.value);
    };

    const onInitiateEmailUpdate = async () =>
    {
        if (processing) return;

        if (!email)
        {
            setError("Email is required");
            return;
        }

        if (email === currentEmail)
        {
            setError("New email is the same as the current one");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            setEmail(email.toLowerCase())
            await initiateAppUserEmailUpdate(email, token);
            setUpdateInitiated(true);
        }
        catch (e: any)
        {
            setError(e.errorMessage || "Failed to initiate email update");
            console.error("Failed to initiate email update:", e);
        }
        finally
        {
            setProcessing(false);
        }
    };

    const onCompleteEmailUpdate = async () =>
    {
        if (processing) return;

        if (!email)
        {
            setError("Email is required");
            return;
        }

        if (!verificationCode)
        {
            setError("Verification code is required");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {

            setEmail(email.toLowerCase())
            await completeAppUserEmailUpdate(email, verificationCode, token);

            //ToDo: show a redirect message before redirecting
            // Redirect to sign-in page
            setToken(null);
            navigate("/sign-in", {
                state: {
                    message: "Your email has been updated successfully. Please sign in with your new email."
                }
            });
        }
        catch (e: any)
        {
            setError(e.errorMessage || "Failed to complete email update");
            console.error("Failed to complete email update:", e);
        }
        finally
        {
            setProcessing(false);
        }
    };

    const resetDialog = () =>
    {
        setEmail("");
        setVerificationCode("");
        setUpdateInitiated(false);
        setError(null);
        onDismiss();
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>
                        Update Email Address
                    </DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {error && <div style={{color: "red", marginBottom: "10px"}}>{error}</div>}

                        {!updateInitiated && (
                            <Text>
                                Updating your email will require verification and you will be signed out from all
                                devices.
                            </Text>
                        )}

                        <Field label="New Email">
                            <Input
                                type="email"
                                value={email}
                                onChange={onEmailChange}
                                maxLength={30}
                                disabled={updateInitiated || processing}
                            />
                        </Field>

                        {updateInitiated && (
                            <Field label="Verification code">
                                <Text size={200} block>
                                    A verification code has been sent to {email}. Please enter it below.
                                </Text>
                                <Input
                                    type="text"
                                    value={verificationCode}
                                    onChange={onVerificationCodeChange}
                                />
                            </Field>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        appearance="primary"
                        shape="circular"
                        disabled={processing}
                        onClick={updateInitiated ? onCompleteEmailUpdate : onInitiateEmailUpdate}
                    >
                        {processing && <Spinner size="tiny"/>}
                        {updateInitiated ? "Verify & Update" : "Continue"}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            appearance="secondary"
                            shape="circular"
                            disabled={processing}
                            onClick={resetDialog}
                        >
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default AppUserEmailUpdateDialog;