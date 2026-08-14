import {useState} from "react";
import {unlinkProvider} from "../../../services/authApi.ts";
import {ResponseError} from "../../models/models.tsx";

interface UseUnlinkProviderFlowOptions
{
    refreshProviders: () => Promise<void>;
    setError: (message: string | undefined) => void;
    setActionLoading: (provider: string | null) => void;
}

export const useUnlinkProviderFlow = ({
    refreshProviders,
    setError,
    setActionLoading,
}: UseUnlinkProviderFlowOptions) =>
{
    const [pendingProvider, setPendingProvider] = useState<string | null>(null);
    const [unlinking, setUnlinking] = useState(false);

    const confirmUnlink = async () =>
    {
        if (!pendingProvider || unlinking) return;

        setUnlinking(true);
        setActionLoading(pendingProvider);
        setError(undefined);
        try
        {
            await unlinkProvider(pendingProvider);
            setPendingProvider(null);
            await refreshProviders();
        }
        catch (error)
        {
            const responseError = error as ResponseError;
            const message = responseError?.reasonCode === "STEP_UP_REQUIRED"
                ? "For your security, please sign in again before changing your sign-in methods."
                : responseError?.errorMessage || "Failed to unlink provider.";
            setError(message);
        }
        finally
        {
            setUnlinking(false);
            setActionLoading(null);
        }
    };

    return {
        pendingProvider,
        unlinking,
        requestUnlink: setPendingProvider,
        confirmUnlink,
        cancelUnlink: () => setPendingProvider(null),
    };
};
