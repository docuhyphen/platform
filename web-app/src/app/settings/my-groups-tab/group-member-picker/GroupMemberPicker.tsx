import MultiPersonPicker from '../../../components/person-picker/multi-person-picker/MultiPersonPicker.tsx';
import {UserContactDto} from '../../../../services/personalContactsApi';
import {toPersonPickerItem} from '../personalGroupContacts.ts';
import {GroupMemberSearch} from '../useGroupMemberSearch.ts';

interface GroupMemberPickerProps
{
    search: GroupMemberSearch;
    existingMemberIds?: string[];
}

const GroupMemberPicker = ({search, existingMemberIds = []}: GroupMemberPickerProps) =>
{
    const availableContacts = search.results.filter(contact =>
        !existingMemberIds.includes(contact.contactAppUserId ?? ""),
    );

    const onSelectionChange = (selectedIds: string[]) =>
    {
        const contacts = [...search.selectedContacts, ...availableContacts];
        search.setSelectedContacts(selectedIds
            .map(id => contacts.find(contact => contact.contactAppUserId === id))
            .filter((contact): contact is UserContactDto => !!contact));
    };

    return (
        <MultiPersonPicker
            id="personal-group-member-picker"
            people={availableContacts.map(toPersonPickerItem)}
            selectedPeople={search.selectedContacts.map(toPersonPickerItem)}
            onSelectionChange={onSelectionChange}
            query={search.query}
            onQueryChange={search.onQueryChange}
            placeholder="Type a name or email"
            noResultsText={search.hasUnregisteredHits
                ? "This contact has not completed registration"
                : "No registered contacts found"}
        />
    );
};

export default GroupMemberPicker;
