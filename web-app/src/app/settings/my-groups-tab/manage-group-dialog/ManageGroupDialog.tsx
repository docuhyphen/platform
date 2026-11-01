import {useState} from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Divider,
    Field,
    Input,
    Spinner,
    Textarea,
} from '@fluentui/react-components';
import {PrincipalGroupDto} from '../../../../services/types/dtos';
import {RemoveMemberTarget} from '../myGroupsTypes.ts';
import {GroupMemberSearch} from '../useGroupMemberSearch.ts';
import ManageGroupMemberList from './ManageGroupMemberList.tsx';
import ManageGroupAddMembers from './ManageGroupAddMembers.tsx';
import {useManageGroupDialogStyles} from './ManageGroupDialogStyles.tsx';

interface ManageGroupDialogProps
{
    group: PrincipalGroupDto | null;
    name: string;
    description: string;
    onNameChange: (name: string) => void;
    onDescriptionChange: (description: string) => void;
    memberSearch: GroupMemberSearch;
    onSave: () => Promise<void>;
    onAddMembers: () => Promise<void>;
    onRemoveMember: (target: RemoveMemberTarget) => void;
    onClose: () => void;
}

const ManageGroupDialog = (
    {
        group,
        name,
        description,
        onNameChange,
        onDescriptionChange,
        memberSearch,
        onSave,
        onAddMembers,
        onRemoveMember,
        onClose,
    }: ManageGroupDialogProps) =>
{
    const styles = useManageGroupDialogStyles();
    const [saving, setSaving] = useState(false);
    const existingMemberIds = group?.members
        .map(m => m.user?.id)
        .filter((id): id is string => !!id) ?? [];

    const handleSave = async () =>
    {
        if (!group || !name.trim()) return;
        setSaving(true);
        try
        {
            await onSave();
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <Dialog modalType="alert"
                open={!!group}>
            <DialogSurface id={"my-groups-tab-dialog-surface-2"}
                           className={styles.manageSurface}>
                <DialogBody id={"my-groups-tab-dialog-body-2"}>
                    <DialogTitle id={"my-groups-tab-dialog-title-2"}>Manage Group</DialogTitle>
                    <DialogContent id={"my-groups-tab-dialog-content-2"}
                                   className={styles.dialogContentColumnLarge}>
                        <Field id={"my-groups-tab-field-4"}
                               label="Group Name"
                               required>
                            <Input
                                id="manage-group-name"
                                value={name}
                                onChange={(_e, d) => onNameChange(d.value)}
                            />
                        </Field>
                        <Field id={"my-groups-tab-field-5"}
                               label="Description">
                            <Textarea
                                id="manage-group-desc"
                                value={description}
                                onChange={(_e, d) => onDescriptionChange(d.value)}
                            />
                        </Field>

                        <Divider id={"my-groups-tab-divider-1"}/>

                        <ManageGroupMemberList group={group}
                                               onRemove={onRemoveMember}/>

                        <ManageGroupAddMembers memberSearch={memberSearch}
                                               existingMemberIds={existingMemberIds}
                                               onAddMembers={onAddMembers}/>
                    </DialogContent>
                    <DialogActions id={"my-groups-tab-dialog-actions-2"}>
                        <Button
                            id="manage-group-save"
                            appearance="primary"
                            shape={"circular"}
                            disabled={saving || !name.trim()}
                            onClick={handleSave}
                        >
                            {saving && <Spinner id={"my-groups-tab-spinner-4"}
                                                size="tiny"/>} Save Changes
                        </Button>
                        <Button
                            id="manage-group-close"
                            appearance="secondary"
                            shape={"circular"}
                            onClick={onClose}
                        >
                            Close
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ManageGroupDialog;
