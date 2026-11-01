import {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {updateOrganizationGroup} from "../../../../services/organizationApi.ts";
import {OrganizationDetailedDto, OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import {
    selectionFromGroup,
    toGroupMemberRequests,
    validateGroupMembers,
} from "../group-members-editor/groupMembersSelection.ts";
import {useGroupMembersSelection} from "../group-members-editor/useGroupMembersSelection.ts";

interface EditGroupDialogOptions
{
    isOpen: boolean;
    appUserPersonOrganization: OrganizationDetailedDto;
    group: OrganizationGroupDetailedDto;
    onDismiss: () => void;
    onComplete: () => void;
}

type UpdateGroupFailure = { status?: number; response?: { status?: number }; message?: string };

export const useEditGroupDialog = (
    {isOpen, appUserPersonOrganization, group, onDismiss, onComplete}: EditGroupDialogOptions,
) =>
{
    const {token} = useAuth();
    const [name, setName] = useState("");
    const [isActive, setIsActive] = useState(true);
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [permissionDenied, setPermissionDenied] = useState(false);
    const selection = useGroupMembersSelection({onLoadError: setError});

    useEffect(() =>
    {
        if (isOpen && group)
        {
            setName(group.name || "");
            setIsActive(group.isActive);
            setPermissionDenied(false);
            setError(null);
            selection.resetQuery();

            selection.loadUsers().then(fetchedUsers =>
            {
                const {members, roles} = selectionFromGroup(group, fetchedUsers);
                selection.setSelection(members, roles);
            });
        }
    }, [isOpen]);

    const onClose = (complete?: boolean) =>
    {
        setError(null);
        if (complete) onComplete();
        else onDismiss();
    };

    const handleSave = async () =>
    {
        if (!appUserPersonOrganization.id || !group?.id || !name.trim()) return;

        const validationError = validateGroupMembers(selection.selectedMembers, selection.memberRoles);
        if (validationError)
        {
            setError(validationError);
            return;
        }

        setSavingData(true);
        setError(null);
        try
        {
            const members = toGroupMemberRequests(selection.selectedMembers, selection.memberRoles);
            await updateOrganizationGroup(
                appUserPersonOrganization.id,
                group.id.toString(),
                {name: name.trim(), isActive, members},
                token || undefined
            );
            onClose(true);
        }
        catch (err: unknown)
        {
            const failure = err as UpdateGroupFailure;
            const status = failure.status ?? failure.response?.status;
            if (status === 403)
            {
                setPermissionDenied(true);
                setError(null);
            }
            else
            {
                setError(failure.message || "Failed to update group");
            }
            console.error("Failed to update group:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    return {
        name,
        setName,
        isActive,
        setIsActive,
        savingData,
        error,
        setError,
        permissionDenied,
        selection,
        handleSave,
        onClose,
    };
};
