import React, {useState} from 'react';
import {Button, Spinner, Text} from '@fluentui/react-components';
import {AddRegular} from '@fluentui/react-icons';
import {useMyGroupsTabStyles} from './MyGroupsTabStyles';
import {RemoveMemberTarget} from './myGroupsTypes.ts';
import {useMyGroups} from './useMyGroups.ts';
import {useGroupMemberSearch} from './useGroupMemberSearch.ts';
import {useManageGroup} from './useManageGroup.ts';
import MyGroupsTable from './my-groups-table/MyGroupsTable.tsx';
import CreateGroupDialog from './create-group-dialog/CreateGroupDialog.tsx';
import ManageGroupDialog from './manage-group-dialog/ManageGroupDialog.tsx';
import RemoveMemberDialog from './remove-member-dialog/RemoveMemberDialog.tsx';
import DeleteGroupDialog from './delete-group-dialog/DeleteGroupDialog.tsx';

const MyGroupsTab: React.FC = () =>
{
    const styles = useMyGroupsTabStyles();
    const memberSearch = useGroupMemberSearch();
    const myGroups = useMyGroups();
    const manage = useManageGroup(myGroups.groups, memberSearch.reset);
    const [createOpen, setCreateOpen] = useState(false);
    const [confirmRemove, setConfirmRemove] = useState<RemoveMemberTarget | null>(null);
    const [confirmDeleteGroupId, setConfirmDeleteGroupId] = useState<string | null>(null);

    const onCreate = async (name: string, description: string | undefined) =>
    {
        const created = await myGroups.createGroup(name, description, memberSearch.selectedContacts);
        if (!created) return false;
        setCreateOpen(false);
        memberSearch.reset();
        await myGroups.loadGroups();
        return true;
    };

    const onCancelCreate = () =>
    {
        setCreateOpen(false);
        memberSearch.reset();
    };

    const onSaveGroupDetails = async () =>
    {
        if (!manage.managingGroup) return;
        const updated = await myGroups.updateGroup(
            manage.managingGroup.id,
            manage.name.trim(),
            manage.description.trim() || undefined,
        );
        if (updated) await myGroups.loadGroups();
    };

    const onAddMembers = async () =>
    {
        if (!manage.managingGroup) return;
        const added = await myGroups.addMembers(manage.managingGroup.id, memberSearch.selectedContacts);
        if (!added) return;
        await myGroups.loadGroups();
        memberSearch.reset();
    };

    const onRemoveMember = async () =>
    {
        if (!confirmRemove) return;
        const removed = await myGroups.removeMember(confirmRemove.groupId, confirmRemove.userId);
        if (!removed) return;
        setConfirmRemove(null);
        await myGroups.loadGroups();
    };

    const onDeleteGroup = async () =>
    {
        if (!confirmDeleteGroupId) return;
        const deleted = await myGroups.deleteGroup(confirmDeleteGroupId);
        if (!deleted) return;
        setConfirmDeleteGroupId(null);
        await myGroups.loadGroups();
    };

    return (
        <div id={"my-groups-tab-div-1"}
             className={styles.container}>
            {myGroups.error && <Text id={"my-groups-tab-text-1"}
                                     className={styles.error}>{myGroups.error}</Text>}

            {myGroups.loading ? (
                <div id={"my-groups-tab-div-2"}
                     className={styles.loading}>
                    <Spinner id={"my-groups-tab-spinner-1"}
                             label="Loading..."
                             size="small"/>
                </div>
            ) : (
                <>
                    <div id={"my-groups-tab-div-3"}
                         className={styles.header}>
                        <span id={"my-groups-tab-span-1"}/>
                        <Button
                            id="create-group-open"
                            icon={<AddRegular/>}
                            appearance="subtle"
                            shape={"circular"}
                            onClick={() => setCreateOpen(true)}
                        >
                            Create Group
                        </Button>
                    </div>
                    <MyGroupsTable groups={myGroups.groups}
                                   onManage={manage.open}
                                   onDelete={group => setConfirmDeleteGroupId(group.id)}/>
                </>
            )}

            <CreateGroupDialog open={createOpen}
                               memberSearch={memberSearch}
                               onCreate={onCreate}
                               onCancel={onCancelCreate}/>
            <ManageGroupDialog group={manage.managingGroup}
                               name={manage.name}
                               description={manage.description}
                               onNameChange={manage.setName}
                               onDescriptionChange={manage.setDescription}
                               memberSearch={memberSearch}
                               onSave={onSaveGroupDetails}
                               onAddMembers={onAddMembers}
                               onRemoveMember={setConfirmRemove}
                               onClose={manage.close}/>
            <RemoveMemberDialog target={confirmRemove}
                                onConfirm={onRemoveMember}
                                onCancel={() => setConfirmRemove(null)}/>
            <DeleteGroupDialog open={!!confirmDeleteGroupId}
                               onConfirm={onDeleteGroup}
                               onCancel={() => setConfirmDeleteGroupId(null)}/>
        </div>
    );
};

export default MyGroupsTab;
