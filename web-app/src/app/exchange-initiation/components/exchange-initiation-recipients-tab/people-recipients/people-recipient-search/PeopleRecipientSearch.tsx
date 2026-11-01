import React from "react";
import {Field, Option, Text} from "@fluentui/react-components";
import {UserContactDto} from "../../../../../../services/personalContactsApi";
import SinglePersonPicker from "../../../../../components/person-picker/single-person-picker/SinglePersonPicker.tsx";
import {PersonPickerItem} from "../../../../../components/person-picker/personPickerTypes.ts";
import {SEND_AS_NEW_OPTION_VALUE, toPersonPickerItem} from "../peopleRecipientContacts.ts";

interface PeopleRecipientSearchProps
{
    results: UserContactDto[];
    query: string;
    trimmedQuery: string;
    isSearching: boolean;
    selectedPersonId?: string;
    showSendAsNewOption: boolean;
    isOwnEmailQuery: boolean;
    onQueryChange: (value: string) => void;
    onPersonSelect: (person: PersonPickerItem | null) => void;
    onSpecialOptionSelect: (value: string) => boolean;
}

const PeopleRecipientSearch: React.FC<PeopleRecipientSearchProps> = (
    {
        results,
        query,
        trimmedQuery,
        isSearching,
        selectedPersonId,
        showSendAsNewOption,
        isOwnEmailQuery,
        onQueryChange,
        onPersonSelect,
        onSpecialOptionSelect,
    }) =>
{
    return (
        <div id={"people-recipients-div-2"}>
            <Field id={"people-recipients-field-2"}
                   label="Find a person or type an email">
                <SinglePersonPicker
                    id={"people-recipients-combobox"}
                    placeholder="Type a name or email"
                    people={results.map(toPersonPickerItem)}
                    query={query}
                    onQueryChange={onQueryChange}
                    onPersonSelect={onPersonSelect}
                    selectedPersonId={selectedPersonId}
                    loading={isSearching}
                    freeform
                    noResultsText="No matching contacts found"
                    onSpecialOptionSelect={onSpecialOptionSelect}
                >
                    {showSendAsNewOption && (
                        <Option
                            id="people-recipients-send-new-option"
                            key={SEND_AS_NEW_OPTION_VALUE}
                            text={`Send to ${trimmedQuery} as a new recipient`}
                            value={SEND_AS_NEW_OPTION_VALUE}
                        >
                            Send to <strong id={"people-recipients-strong-1"}>{trimmedQuery}</strong> as a new recipient
                        </Option>
                    )}
                </SinglePersonPicker>
                {isOwnEmailQuery && (
                    <Text id={"people-recipients-text-2"}
                          size={200}>You cannot share with your own email address.</Text>
                )}
            </Field>
        </div>
    );
};

export default PeopleRecipientSearch;
