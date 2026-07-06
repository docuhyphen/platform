import {OrganizationDetailedDto, OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import AddGroupDialog from "../add-group-dialog/AddGroupDialog.tsx";
import EditGroupDialog from "../edit-group-dialog/EditGroupDialog.tsx";
import GroupDeleteDialog from "../group-delete-dialog/GroupDeleteDialog.tsx";

interface OrganizationGroupsDialogsProps
{
    organizationId: string;
    organization: OrganizationDetailedDto;
    selectedGroup: OrganizationGroupDetailedDto | null;
    isAddOpen: boolean;
    isEditOpen: boolean;
    isDeleteOpen: boolean;
    onAddDismiss: () => void;
    onEditDismiss: () => void;
    onDeleteDismiss: () => void;
    onComplete: () => void;
}

const OrganizationGroupsDialogs = ({
    organizationId,
    organization,
    selectedGroup,
    isAddOpen,
    isEditOpen,
    isDeleteOpen,
    onAddDismiss,
    onEditDismiss,
    onDeleteDismiss,
    onComplete
}: OrganizationGroupsDialogsProps) => (
    <>
        <AddGroupDialog
            isOpen={isAddOpen}
            onDismiss={onAddDismiss}
            organizationId={organizationId}
            onComplete={onComplete}
        />
        <EditGroupDialog
            isOpen={isEditOpen}
            onDismiss={onEditDismiss}
            appUserPersonOrganization={organization}
            group={selectedGroup}
            onComplete={onComplete}
        />
        <GroupDeleteDialog
            isOpen={isDeleteOpen}
            organizationId={organizationId}
            group={selectedGroup}
            onDismiss={onDeleteDismiss}
            onDeleted={onComplete}
        />
    </>
);

export default OrganizationGroupsDialogs;
