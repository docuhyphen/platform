import {AppUserPublicDto} from "../../../models/models.tsx";
import AddAppUserDialog from "../add-app-user-dialog/AddAppUserDialog.tsx";
import EditUserDialog from "../app-user-edit-dialog/EditUserDialog.tsx";

interface OrganizationPeopleDialogsProps
{
    organizationId?: string;
    selectedUser: AppUserPublicDto | null;
    isAddOpen: boolean;
    isEditOpen: boolean;
    onAddDismiss: () => void;
    onEditDismiss: () => void;
    onComplete: () => void;
}

const OrganizationPeopleDialogs = ({
    organizationId,
    selectedUser,
    isAddOpen,
    isEditOpen,
    onAddDismiss,
    onEditDismiss,
    onComplete
}: OrganizationPeopleDialogsProps) => (
    <>
        <AddAppUserDialog
            isOpen={isAddOpen}
            onDismiss={onAddDismiss}
            organizationId={organizationId}
            onComplete={onComplete}
        />
        <EditUserDialog
            isOpen={isEditOpen}
            onDismiss={onEditDismiss}
            organizationId={organizationId}
            user={selectedUser}
            onComplete={onComplete}
        />
    </>
);

export default OrganizationPeopleDialogs;
