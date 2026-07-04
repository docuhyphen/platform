import React, {useState} from "react";
import {AppUserPublicDto} from "../../../models/models.tsx";
import {Divider, Field, Spinner} from "@fluentui/react-components";
import {useAuth} from "../../../../context/AuthContext.tsx";
import MultiPersonPicker from "../../../components/person-picker/multi-person-picker/MultiPersonPicker.tsx";
import {
    matchesPersonQuery,
    PersonPickerItem,
} from "../../../components/person-picker/personPickerTypes.ts";

const toPersonPickerItem = (user: AppUserPublicDto): PersonPickerItem => ({
    id: user.id,
    email: user.email,
    firstName: user.person?.firstName,
    lastName: user.person?.lastName,
    avatarUrl: user.avatarUrl,
});

const MyOrgRecipients: React.FC<{
    orgUsers: AppUserPublicDto[];
    isLoadingUsers: boolean;
    selectedInternalRecipients: AppUserPublicDto[];
    setSelectedInternalParticipants: (participants: AppUserPublicDto[]) => void;
    setInternalParticipants?: (users: AppUserPublicDto[]) => void;
}> = ({
          orgUsers,
          isLoadingUsers,
          selectedInternalRecipients,
          setSelectedInternalParticipants,
          setInternalParticipants
      }) =>
{
    const {appUser} = useAuth();
    const [internalRecipientsInputValue, setInternalRecipientsInputValue] = useState<string>("");

    const allUsers = [...orgUsers, ...selectedInternalRecipients]
        .filter((user, index, users) => users.findIndex(candidate => candidate.id === user.id) === index)
        .filter(user => user.id !== appUser?.id);
    const people = allUsers.map(toPersonPickerItem).filter(person => person.id);
    const filteredPeople = people.filter(person => matchesPersonQuery(person, internalRecipientsInputValue));
    const selectedPeople = selectedInternalRecipients.map(toPersonPickerItem).filter(person => person.id);

    const onInternalRecipientSelect = (selectedIds: string[]) =>
    {
        const newSelectedRecipients = selectedIds.map(
            optionValue => allUsers.find(user => user.id === optionValue)
        ).filter(Boolean)
            .filter(user => user.id !== appUser?.id) as AppUserPublicDto[];

        setSelectedInternalParticipants(newSelectedRecipients);
        setInternalRecipientsInputValue("");

        if (setInternalParticipants)
        {
            setInternalParticipants(newSelectedRecipients);
        }
    };


    return (
        <>
            <Divider alignContent="start">
                Participants from your organization
            </Divider>
            <Field>
                {isLoadingUsers ? (
                    <Spinner size="tiny" label="Loading users..."/>
                ) : (
                    <MultiPersonPicker
                        id="my-org-recipients-combobox"
                        people={filteredPeople}
                        selectedPeople={selectedPeople}
                        onSelectionChange={onInternalRecipientSelect}
                        query={internalRecipientsInputValue}
                        onQueryChange={setInternalRecipientsInputValue}
                        placeholder="Find additional participants"
                        noResultsText="No matching organization users found"
                    />
                )}
            </Field>
        </>
    );
};


export default MyOrgRecipients;
