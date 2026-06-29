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
import {
    completeAppUserEmailUpdate,
    confirmOldAppUserEmailForUpdate,
    initiateAppUserEmailUpdate
} from "../../../services/appUserApi";
import {useAppUserEmailUpdateDialogStyles} from "./AppUserEmailUpdateDialogStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {isValidEmail} from "../../../utils/helpers.ts";

interface AppUserEmailUpdateDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    currentEmail?: string;
}

enum UpdateStage
{
    ENTER_EMAIL,
    CONFIRM_OLD,
    CONFIRM_NEW
}

const AppUserEmailUpdateDialog: React.FC<AppUserEmailUpdateDialogProps> = (
    {
        isOpen,
        onDismiss,
        currentEmail
    }) =>
{
    const styles = useAppUserEmailUpdateDialogStyles()
    const globalStyles = useGlobalStyles()
    const {token, setToken} = useAuth();
    const navigate = useNavigate();
    const [email, setEmail] = useState<string>("");
    const [processing, setProcessing] = useState(false);
    const [stage, setStage] = useState<UpdateStage>(UpdateStage.ENTER_EMAIL);
    const [oldEmailCode, setOldEmailCode] = useState("");
    const [newEmailCode, setNewEmailCode] = useState("");
    const [error, setError] = useState<string | null>(null);

    const extractErrorMessage = (e: any): string =>
    {
        if (typeof e === "string") return e;
        const msg = e?.errorMessage || e?.message || e?.response?.data?.errorMessage || e?.response?.data?.message;
        if (msg) return msg;
        return "Something went wrong. Please try again in a moment.";
    }

    const onInitiate = async () =>
    {
        if (processing) return;

        const trimmed = email.trim().toLowerCase();

        if (!trimmed)
        {
            setError("Email is required");
            return;
        }

        if (!isValidEmail(trimmed))
        {
            setError("Please enter a valid email address");
            return;
        }

        if (trimmed === currentEmail?.toLowerCase())
        {
            setError("New email is the same as the current one");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            setEmail(trimmed);
            await initiateAppUserEmailUpdate(trimmed, token);
            setStage(UpdateStage.CONFIRM_OLD);
        }
        catch (e: any)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to initiate email update:", e);
        }
        finally
        {
            setProcessing(false);
        }
    };

    const onConfirmOld = async () =>
    {
        if (processing) return;

        if (!oldEmailCode.trim())
        {
            setError("Verification code is required");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await confirmOldAppUserEmailForUpdate(oldEmailCode.trim(), token);
            setStage(UpdateStage.CONFIRM_NEW);
        }
        catch (e: any)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to confirm old email:", e);
        }
        finally
        {
            setProcessing(false);
        }
    };

    const onComplete = async () =>
    {
        if (processing) return;

        if (!newEmailCode.trim())
        {
            setError("Verification code is required");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await completeAppUserEmailUpdate(email, newEmailCode.trim(), token);

            setToken(null);
            navigate("/sign-in", {
                state: {
                    message: "Your email has been updated successfully. Please sign in with your new email."
                }
            });
        }
        catch (e: any)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to complete email update:", e);
        }
        finally
        {
            setProcessing(false);
        }
    };

    const onPrimaryClick = () =>
    {
        if (stage === UpdateStage.ENTER_EMAIL) onInitiate();
        else if (stage === UpdateStage.CONFIRM_OLD) onConfirmOld();
        else onComplete();
    }

    const resetDialog = () =>
    {
        setEmail("");
        setOldEmailCode("");
        setNewEmailCode("");
        setStage(UpdateStage.ENTER_EMAIL);
        setError(null);
        onDismiss();
    };

    const primaryLabel = () =>
    {
        if (stage === UpdateStage.ENTER_EMAIL) return "Continue";
        if (stage === UpdateStage.CONFIRM_OLD) return "Verify current email";
        return "Verify & update";
    }

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>
                        Update Email Address
                    </DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        { error && <div className={styles.errorContainer}>{error}</div>}

                        {stage === UpdateStage.ENTER_EMAIL && (
                            <Text>
                                Updating your email is a two-step verification process: we'll send a code to your
                                current email first, then to your new email. You'll be signed out from all
                                devices on completion.
                            </Text>
                        )}

                        <Field label="New Email">
                            <Input
                                id={"app-user-email-update-new-email-input"}
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") onPrimaryClick(); }}
                                maxLength={254}
                                disabled={stage !== UpdateStage.ENTER_EMAIL || processing}
                            />
                        </Field>

                        {stage === UpdateStage.CONFIRM_OLD && (
                            <Field label="Code sent to your current email">
                                <Text size={200} block>
                                    A verification code has been sent to {currentEmail}. Please enter it below.
                                </Text>
                                <Input
                                    id={"app-user-email-update-old-code-input"}
                                    type="text"
                                    value={oldEmailCode}
                                    onChange={(e) => setOldEmailCode(e.target.value)}
                                    onKeyDown={(e) => { if (e.key === "Enter") onPrimaryClick(); }}
                                    maxLength={10}
                                />
                            </Field>
                        )}

                        {stage === UpdateStage.CONFIRM_NEW && (
                            <Field label="Code sent to your new email">
                                <Text size={200} block>
                                    A verification code has been sent to {email}. Please enter it below.
                                </Text>
                                <Input
                                    id={"app-user-email-update-new-code-input"}
                                    type="text"
                                    value={newEmailCode}
                                    onChange={(e) => setNewEmailCode(e.target.value)}
                                    onKeyDown={(e) => { if (e.key === "Enter") onPrimaryClick(); }}
                                    maxLength={10}
                                />
                            </Field>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id={"app-user-email-update-submit-btn"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={processing}
                        onClick={onPrimaryClick}
                    >
                        {processing && <Spinner size="tiny"/>}
                        {primaryLabel()}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"app-user-email-update-cancel-btn"}
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
