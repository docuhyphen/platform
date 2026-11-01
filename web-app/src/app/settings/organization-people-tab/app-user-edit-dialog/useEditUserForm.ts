import {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {AppUserPublicDto} from "../../../models/models.tsx";
import {OrganizationRoleName} from '../../../../services/types/roles.ts';
import {updateOrganizationUser} from "../../../../services/organizationApi.ts";
import {extractDialogErrorMessage} from "../../components/dialogErrorMessage.ts";

interface EditUserFormOptions
{
    organizationId: string;
    user: AppUserPublicDto | null;
    onDismiss: () => void;
    onComplete: () => void;
}

export const useEditUserForm = ({organizationId, user, onDismiss, onComplete}: EditUserFormOptions) =>
{
    const {token} = useAuth();
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [roles, setRoles] = useState<OrganizationRoleName[]>([]);
    const [isActive, setIsActive] = useState(true);
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (user)
        {
            setFirstName(user.person?.firstName || "");
            setLastName(user.person?.lastName || "");
            setRoles(user.organizationRoles as OrganizationRoleName[]);
            setIsActive(user.isActive);
        }
    }, [user]);

    const handleSave = async () =>
    {
        if (!organizationId || !user?.id) return;

        if (!firstName.trim() || !lastName.trim())
        {
            setError("First name and last name are required.");
            return;
        }

        setSavingData(true);
        setError(null);

        try
        {
            await updateOrganizationUser(
                organizationId,
                user.id.toString(),
                {
                    rolesToAdd: roles.filter((role) => !user.organizationRoles.includes(role)),
                    rolesToRemove: user.organizationRoles.filter((role) => !roles.includes(role as OrganizationRoleName)) as OrganizationRoleName[],
                    isActive,
                    person: {
                        firstName: firstName.trim(),
                        lastName: lastName.trim()
                    }
                },
                token || undefined
            );

            onComplete();
        }
        catch (err: unknown)
        {
            setError(extractDialogErrorMessage(err, "Something went wrong. Please try again in a moment."));
            console.error("Failed to update user:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    const onClose = () =>
    {
        setError(null);
        onDismiss();
    };

    const hasChanges =
        firstName !== user?.person?.firstName ||
        lastName !== user?.person?.lastName ||
        roles.slice().sort().join(',') !== user?.organizationRoles.slice().sort().join(',') ||
        isActive !== user?.isActive;

    const isFormValid = firstName && lastName && roles.length > 0;

    const activeSwitchLocked = user?.organizationRoles.includes(OrganizationRoleName.ORG_ADMIN) ||
        user?.organizationRoles.includes(OrganizationRoleName.ORG_OWNER);

    return {
        firstName,
        setFirstName,
        lastName,
        setLastName,
        roles,
        setRoles,
        isActive,
        setIsActive,
        savingData,
        error,
        hasChanges,
        isFormValid,
        activeSwitchLocked,
        handleSave,
        onClose,
    };
};
