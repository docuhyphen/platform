import {useState} from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Input,
    Spinner,
    Textarea,
} from '@fluentui/react-components';
import {GroupMemberSearch} from '../useGroupMemberSearch.ts';
import GroupMemberPicker from '../group-member-picker/GroupMemberPicker.tsx';
import {useCreateGroupDialogStyles} from './CreateGroupDialogStyles.tsx';

interface CreateGroupDialogProps
{
    open: boolean;
    memberSearch: GroupMemberSearch;
    onCreate: (name: string, description: string | undefined) => Promise<boolean>;
    onCancel: () => void;
}

const CreateGroupDialog = ({open, memberSearch, onCreate, onCancel}: CreateGroupDialogProps) =>
{
    const styles = useCreateGroupDialogStyles();
    const [newName, setNewName] = useState('');
    const [newDesc, setNewDesc] = useState('');
    const [creating, setCreating] = useState(false);

    const handleCreate = async () =>
    {
        if (!newName.trim() || memberSearch.selectedContacts.length === 0) return;
        setCreating(true);
        try
        {
            const created = await onCreate(newName.trim(), newDesc.trim() || undefined);
            if (created)
            {
                setNewName('');
                setNewDesc('');
            }
        }
        finally
        {
            setCreating(false);
        }
    };

    const handleCancel = () =>
    {
        setNewName('');
        setNewDesc('');
        onCancel();
    };

    return (
        <Dialog modalType="alert"
                open={open}>
            <DialogSurface id={"my-groups-tab-dialog-surface-1"}>
                <DialogBody id={"my-groups-tab-dialog-body-1"}>
                    <DialogTitle id={"my-groups-tab-dialog-title-1"}>Create Personal Group</DialogTitle>
                    <DialogContent id={"my-groups-tab-dialog-content-1"}
                                   className={styles.dialogContentColumn}>
                        <Field id={"my-groups-tab-field-1"}
                               label="Group Name"
                               required>
                            <Input
                                id="create-group-name"
                                value={newName}
                                onChange={(_e, d) => setNewName(d.value)}
                            />
                        </Field>
                        <Field id={"my-groups-tab-field-2"}
                               label="Description">
                            <Textarea
                                id="create-group-desc"
                                value={newDesc}
                                onChange={(_e, d) => setNewDesc(d.value)}
                            />
                        </Field>
                        <Field id={"my-groups-tab-field-3"}
                               label="Members"
                               required
                               hint="Search and add at least one member to the group.">
                            <GroupMemberPicker search={memberSearch}/>
                        </Field>
                    </DialogContent>
                    <DialogActions id={"my-groups-tab-dialog-actions-1"}>
                        <Button
                            id="create-group-submit"
                            appearance="primary"
                            shape={"circular"}
                            disabled={creating || !newName.trim() || memberSearch.selectedContacts.length === 0}
                            onClick={handleCreate}
                        >
                            {creating && <Spinner id={"my-groups-tab-spinner-2"}
                                                  size="tiny"/>} Create
                        </Button>
                        <Button
                            id="create-group-cancel"
                            appearance="secondary"
                            shape={"circular"}
                            onClick={handleCancel}
                        >
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default CreateGroupDialog;
