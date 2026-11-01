import {useState} from "react";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../../context/AuthContext.tsx";
import {
    completeAppUserEmailUpdate,
    confirmOldAppUserEmailForUpdate,
    initiateAppUserEmailUpdate
} from "../../../services/appUserApi";
import {isValidEmail} from "../../../utils/helpers.ts";
import {AppUserEmailUpdateStage, extractAppUserEmailUpdateErrorMessage} from "./appUserEmailUpdateStage.ts";

interface UseAppUserEmailUpdateArgs
{
    currentEmail?: string;
    onDismiss: () => void;
}

export const useAppUserEmailUpdate = ({currentEmail, onDismiss}: UseAppUserEmailUpdateArgs) =>
{
    const {token, setToken} = useAuth();
    const navigate = useNavigate();
    const [email, setEmail] = useState<string>("");
    const [processing, setProcessing] = useState(false);
    const [stage, setStage] = useState<AppUserEmailUpdateStage>(AppUserEmailUpdateStage.ENTER_EMAIL);
    const [oldEmailCode, setOldEmailCode] = useState("");
    const [newEmailCode, setNewEmailCode] = useState("");
    const [error, setError] = useState<string | null>(null);

    const run = async (action: () => Promise<void>, failureLog: string) =>
    {
        setProcessing(true);
        setError(null);
        try
        {
            await action();
        }
        catch (e: unknown)
        {
            setError(extractAppUserEmailUpdateErrorMessage(e));
            console.error(failureLog, e);
        }
        finally
        {
            setProcessing(false);
        }
    };

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

        await run(async () =>
        {
            setEmail(trimmed);
            await initiateAppUserEmailUpdate(trimmed, token);
            setStage(AppUserEmailUpdateStage.CONFIRM_OLD);
        }, "Failed to initiate email update:");
    };

    const onConfirmOld = async () =>
    {
        if (processing) return;

        if (!oldEmailCode.trim())
        {
            setError("Verification code is required");
            return;
        }

        await run(async () =>
        {
            await confirmOldAppUserEmailForUpdate(oldEmailCode.trim(), token);
            setStage(AppUserEmailUpdateStage.CONFIRM_NEW);
        }, "Failed to confirm old email:");
    };

    const onComplete = async () =>
    {
        if (processing) return;

        if (!newEmailCode.trim())
        {
            setError("Verification code is required");
            return;
        }

        await run(async () =>
        {
            await completeAppUserEmailUpdate(email, newEmailCode.trim(), token);

            setToken(null);
            navigate("/sign-in", {
                state: {
                    message: "Your email has been updated successfully. Please sign in with your new email."
                }
            });
        }, "Failed to complete email update:");
    };

    const onPrimaryClick = () =>
    {
        if (stage === AppUserEmailUpdateStage.ENTER_EMAIL) void onInitiate();
        else if (stage === AppUserEmailUpdateStage.CONFIRM_OLD) void onConfirmOld();
        else void onComplete();
    };

    const resetDialog = () =>
    {
        setEmail("");
        setOldEmailCode("");
        setNewEmailCode("");
        setStage(AppUserEmailUpdateStage.ENTER_EMAIL);
        setError(null);
        onDismiss();
    };

    return {
        email,
        setEmail,
        processing,
        stage,
        oldEmailCode,
        setOldEmailCode,
        newEmailCode,
        setNewEmailCode,
        error,
        onPrimaryClick,
        resetDialog,
    };
};
