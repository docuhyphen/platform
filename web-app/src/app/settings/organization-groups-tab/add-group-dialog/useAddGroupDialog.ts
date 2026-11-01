import {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {addOrganizationGroup} from "../../../../services/organizationApi.ts";
import {PrincipalGroupRoleName} from "../../../../services/types/roles";
import {toGroupMemberRequests, validateGroupMembers} from "../group-members-editor/groupMembersSelection.ts";
import {useGroupMembersSelection} from "../group-members-editor/useGroupMembersSelection.ts";

interface AddGroupDialogOptions
{
    isOpen: boolean;
    organizationId: string;
    onDismiss: () => void;
    onComplete: () => void;
}

export const useAddGroupDialog = ({isOpen, organizationId, onDismiss, onComplete}: AddGroupDialogOptions) =>
{
    const {token, appUser} = useAuth();
    const [name, setName] = useState("");
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const selection = useGroupMembersSelection({
        defaultRoleFor: uid => uid === appUser?.id ? PrincipalGroupRoleName.OWNER : PrincipalGroupRoleName.MEMBER,
        onLoadError: setError,
    });

    const resetForm = () =>
    {
        setName("");
        selection.reset();
        setError(null);
    };

    useEffect(() =>
    {
        if (isOpen)
        {
            resetForm();
            if (organizationId) selection.loadUsers();
        }
    }, [isOpen]);

    const handleSave = async () =>
    {
        if (!organizationId || !name.trim()) return;
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
            await addOrganizationGroup(organizationId, {name: name.trim(), members}, token || undefined);
            resetForm();
            onComplete();
        }
        catch (err: unknown)
        {
            setError(err instanceof Error && err.message ? err.message : "Failed to create group");
            console.error("Failed to create group:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    const onClose = () =>
    {
        resetForm();
        onDismiss();
    };

    return {name, setName, savingData, error, selection, handleSave, onClose};
};
