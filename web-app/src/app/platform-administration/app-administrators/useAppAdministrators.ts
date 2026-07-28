import {useCallback, useEffect, useMemo, useState} from "react";
import {useAuth} from "../../../context/AuthContext.tsx";
import {fetchAppAdmins, revokeAppAdmin} from "../../../services/appRoleApi.ts";
import {AppAdminDto} from "../../../services/types/dtos.ts";

const extractErrorStatus = (error: unknown): number | undefined =>
{
    const value = error as {
        status?: number;
        statusCode?: number;
        response?: {status?: number};
    } | null | undefined;
    return value?.status ?? value?.response?.status ?? value?.statusCode;
};

const extractErrorMessage = (error: unknown, fallback: string): string =>
{
    const value = error as {
        errorMessage?: string;
        message?: string;
    } | null | undefined;
    return value?.errorMessage || value?.message || fallback;
};

const looksLikeLastAdminError = (error: unknown): boolean =>
{
    const message = extractErrorMessage(error, "").toLowerCase();
    return message.includes("last") && message.includes("admin");
};

export const useAppAdministrators = () =>
{
    const {appUser} = useAuth();
    const [admins, setAdmins] = useState<AppAdminDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [noAccess, setNoAccess] = useState(false);
    const [busy, setBusy] = useState(false);
    const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);

    const loadAdmins = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        setNoAccess(false);
        try
        {
            setAdmins(await fetchAppAdmins());
        }
        catch (loadError: unknown)
        {
            if (extractErrorStatus(loadError) === 403)
            {
                setNoAccess(true);
            }
            else
            {
                setError(extractErrorMessage(loadError, "Failed to load App Administrators"));
            }
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        void loadAdmins();
    }, [loadAdmins]);

    const handleRevoke = async (assignmentId: string) =>
    {
        const target = admins.find((admin) => admin.assignmentId === assignmentId);
        if (target?.appUserId === appUser?.id)
        {
            setError("You cannot revoke your own App Administrator access.");
            return;
        }

        setBusy(true);
        setError(null);
        try
        {
            await revokeAppAdmin(assignmentId);
            await loadAdmins();
        }
        catch (revokeError: unknown)
        {
            const isLastAdmin = extractErrorStatus(revokeError) === 409 ||
                looksLikeLastAdminError(revokeError);
            setError(isLastAdmin
                ? "You cannot revoke the last App Administrator. Grant the role to another user first."
                : extractErrorMessage(revokeError, "Failed to revoke App Administrator"));
        }
        finally
        {
            setBusy(false);
        }
    };

    const existingAdminUserIds = useMemo(
        () => new Set(admins.flatMap((admin) => admin.appUserId ? [admin.appUserId] : [])),
        [admins],
    );

    const handleAddComplete = () =>
    {
        setIsAddDialogOpen(false);
        void loadAdmins();
    };

    return {
        admins,
        loading,
        error,
        noAccess,
        busy,
        isAddDialogOpen,
        existingAdminUserIds,
        setIsAddDialogOpen,
        handleRevoke,
        handleAddComplete,
    };
};
