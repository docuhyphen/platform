import {useState} from 'react';
import {Button, Field, Spinner} from '@fluentui/react-components';
import {GroupMemberSearch} from '../useGroupMemberSearch.ts';
import GroupMemberPicker from '../group-member-picker/GroupMemberPicker.tsx';
import {useManageGroupDialogStyles} from './ManageGroupDialogStyles.tsx';

interface ManageGroupAddMembersProps
{
    memberSearch: GroupMemberSearch;
    existingMemberIds: string[];
    onAddMembers: () => Promise<void>;
}

const ManageGroupAddMembers = ({memberSearch, existingMemberIds, onAddMembers}: ManageGroupAddMembersProps) =>
{
    const styles = useManageGroupDialogStyles();
    const [addingMembers, setAddingMembers] = useState(false);

    const handleAddMembers = async () =>
    {
        if (memberSearch.selectedContacts.length === 0) return;
        setAddingMembers(true);
        try
        {
            await onAddMembers();
        }
        finally
        {
            setAddingMembers(false);
        }
    };

    return (
        <Field id={"my-groups-tab-field-6"}
               label="Add Members"
               hint="Only contacts with a registered account can be added.">
            <div id={"my-groups-tab-div-7"}
                 className={styles.addMembersRow}>
                <GroupMemberPicker search={memberSearch}
                                   existingMemberIds={existingMemberIds}/>
                <Button
                    id="manage-group-add-members"
                    appearance="primary"
                    shape={"circular"}
                    disabled={addingMembers || memberSearch.selectedContacts.length === 0}
                    onClick={handleAddMembers}
                >
                    {addingMembers && <Spinner id={"my-groups-tab-spinner-3"}
                                               size="tiny"/>} Add Members
                </Button>
            </div>
        </Field>
    );
};

export default ManageGroupAddMembers;
