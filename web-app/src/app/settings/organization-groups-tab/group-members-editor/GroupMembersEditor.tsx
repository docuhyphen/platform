import {Field, Text} from "@fluentui/react-components";
import MultiPersonPicker from "../../../components/person-picker/multi-person-picker/MultiPersonPicker.tsx";
import {GroupMembersEditorIds} from "./groupMembersEditorIds.ts";
import {toPersonPickerItem} from "./groupMembersSelection.ts";
import {GroupMembersSelection} from "./useGroupMembersSelection.ts";
import GroupMembersTable from "./GroupMembersTable.tsx";
import {useGroupMembersEditorStyles} from "./GroupMembersEditorStyles.tsx";

interface GroupMembersEditorProps
{
    ids: GroupMembersEditorIds;
    selection: GroupMembersSelection;
    disabled?: boolean;
}

const GroupMembersEditor = ({ids, selection, disabled = false}: GroupMembersEditorProps) =>
{
    const styles = useGroupMembersEditorStyles();

    return (
        <>
            <Field
                id={ids.field}
                label="Add members"
                hint="Search by name or email to add org members."
                className={styles.addMembersField}
            >
                <MultiPersonPicker
                    id={ids.picker}
                    people={selection.pickerOptions.map(toPersonPickerItem)}
                    selectedPeople={selection.memberUsers.map(toPersonPickerItem)}
                    onSelectionChange={selection.onMemberSelectionChange}
                    query={selection.query}
                    onQueryChange={selection.onQueryChange}
                    placeholder="Type a name or email"
                    disabled={disabled}
                    noResultsText="No matching organization members found"
                />
            </Field>

            {selection.memberUsers.length === 0 ? (
                <Text id={ids.emptyText}
                      size={200}
                      italic>
                    No members added yet. Use the search above to add members.
                </Text>
            ) : (
                <GroupMembersTable ids={ids}
                                   selection={selection}
                                   disabled={disabled}/>
            )}
        </>
    );
};

export default GroupMembersEditor;
