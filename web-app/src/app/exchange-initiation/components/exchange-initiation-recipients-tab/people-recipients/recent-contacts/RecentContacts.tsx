import React from "react";
import {Button, Spinner, Text} from "@fluentui/react-components";
import {UserContactDto} from "../../../../../../services/personalContactsApi";
import PersonOption from "../../../../../components/person-picker/person-option/PersonOption.tsx";
import {toPersonPickerItem} from "../peopleRecipientContacts.ts";
import {useRecentContactsStyles} from "./RecentContactsStyles.tsx";

interface RecentContactsProps
{
    isLoading: boolean;
    contacts: UserContactDto[];
    recipientEmail?: string;
    newRecipientEmail?: string;
    onSelect: (contact: UserContactDto) => void;
}

const RecentContacts: React.FC<RecentContactsProps> = (
    {
        isLoading,
        contacts,
        recipientEmail,
        newRecipientEmail,
        onSelect,
    }) =>
{
    const styles = useRecentContactsStyles();

    if (isLoading)
    {
        return <Spinner id={"people-recipients-spinner-1"}
                        size="tiny"
                        label="Loading recent contacts..."/>;
    }
    if (contacts.length === 0)
    {
        return (
            <Text id={"people-recipients-text-1"}
                  size={200}
                  italic>
                No recent external contacts yet. People will appear here once you've exchanged with them.
                Users in your organization are shown under My Organization.
            </Text>
        );
    }
    return (
        <div id={"people-recipients-div-1"}
             className={styles.recentContactsRow}>
            {contacts.map(c =>
            {
                const isActive = recipientEmail === c.email || newRecipientEmail === c.email;
                return (
                    <Button
                        key={c.email}
                        id={`recent-contact-btn-${c.email}`}
                        size="small"
                        shape="circular"
                        className={isActive ? styles.activeRecentContact : undefined}
                        appearance={isActive ? 'primary' : 'outline'}
                        onClick={() => onSelect(c)}>
                        <PersonOption
                            size={"extra-small"}
                            id={`recent-contact-persona-${c.email}`}
                            person={toPersonPickerItem(c)}
                        />
                    </Button>
                );
            })}
        </div>
    );
};

export default RecentContacts;
