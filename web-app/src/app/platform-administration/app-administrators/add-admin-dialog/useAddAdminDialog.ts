import {useEffect, useMemo, useState} from "react";
import {grantAppAdmin, searchAppAdminCandidates} from "../../../../services/appRoleApi.ts";
import {AppUserSearchResult} from "../../../../services/types/dtos.ts";
import {PersonPickerItem} from "../../../components/person-picker/personPickerTypes.ts";

const toPersonPickerItem = (user: AppUserSearchResult): PersonPickerItem => ({
    id: user.id,
    email: user.email,
    firstName: user.firstName,
    lastName: user.lastName,
    avatarUrl: user.avatarUrl,
});

interface UseAddAdminDialogOptions
{
    isOpen: boolean;
    onDismiss: () => void;
    existingAdminUserIds: Set<string>;
    onComplete: () => void;
}

export const useAddAdminDialog = ({
    isOpen,
    onDismiss,
    existingAdminUserIds,
    onComplete,
}: UseAddAdminDialogOptions) =>
{
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResults, setSearchResults] = useState<AppUserSearchResult[]>([]);
    const [selectedUsers, setSelectedUsers] = useState<AppUserSearchResult[]>([]);
    const [searching, setSearching] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resetForm = () =>
    {
        setSearchQuery("");
        setSearchResults([]);
        setSelectedUsers([]);
        setError(null);
        setSearching(false);
    };

    useEffect(() =>
    {
        if (!isOpen)
        {
            resetForm();
        }
    }, [isOpen]);

    useEffect(() =>
    {
        const query = searchQuery.trim();
        if (query.length < 2)
        {
            setSearchResults([]);
            setSearching(false);
            return;
        }

        let cancelled = false;
        setSearching(true);
        const handle = window.setTimeout(() =>
        {
            void searchAppAdminCandidates(query)
                .then((users) =>
                {
                    if (!cancelled)
                    {
                        setSearchResults(users.filter((user) => !existingAdminUserIds.has(user.id)));
                    }
                })
                .catch(() => !cancelled && setSearchResults([]))
                .finally(() => !cancelled && setSearching(false));
        }, 250);

        return () =>
        {
            cancelled = true;
            window.clearTimeout(handle);
        };
    }, [searchQuery, existingAdminUserIds]);

    const onSelectionChange = (selectedIds: string[]) =>
    {
        const candidates = [...selectedUsers, ...searchResults];
        setSelectedUsers(selectedIds.flatMap((id) =>
        {
            const user = candidates.find((candidate) => candidate.id === id);
            return user ? [user] : [];
        }));
    };

    const handleAdd = async () =>
    {
        if (selectedUsers.length === 0)
        {
            return;
        }
        setBusy(true);
        setError(null);
        try
        {
            await Promise.all(selectedUsers.map((user) => grantAppAdmin({appUserId: user.id})));
            resetForm();
            onComplete();
        }
        catch (grantError: unknown)
        {
            const value = grantError as {errorMessage?: string; message?: string} | null;
            setError(value?.errorMessage || value?.message || "Failed to grant App Administrator");
        }
        finally
        {
            setBusy(false);
        }
    };

    const handleClose = () =>
    {
        resetForm();
        onDismiss();
    };

    return {
        searchQuery,
        searching,
        busy,
        error,
        selectedCount: selectedUsers.length,
        searchResultItems: useMemo(() => searchResults.map(toPersonPickerItem), [searchResults]),
        selectedUserItems: useMemo(() => selectedUsers.map(toPersonPickerItem), [selectedUsers]),
        submitLabel: selectedUsers.length > 1 ? `Add ${selectedUsers.length} Admins` : "Add Admin",
        setSearchQuery,
        onSelectionChange,
        handleAdd,
        handleClose,
    };
};
