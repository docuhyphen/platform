import React, {useRef, useState} from "react";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {Button, Combobox, ComboboxProps, Divider, Field, Option, Spinner} from "@fluentui/react-components";
import {Dismiss12Regular} from "@fluentui/react-icons";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {useExchangeInitiationRecipientsTabStyles} from "./ExchangeInitiationRecipientsTabStyles.tsx";

const MyOrgRecipients: React.FC<{
    orgUsers: AppUserDetailedDto[];
    isLoadingUsers: boolean;
    selectedInternalRecipients: AppUserDetailedDto[];
    setSelectedInternalParticipants: (participants: AppUserDetailedDto[]) => void;
    setInternalParticipants?: (users: AppUserDetailedDto[]) => void;
}> = ({
          orgUsers,
          isLoadingUsers,
          selectedInternalRecipients,
          setSelectedInternalParticipants,
          setInternalParticipants
      }) =>
{
    const {appUser} = useAuth();
    const styles = useExchangeInitiationRecipientsTabStyles();
    const [internalRecipientsInputValue, setInternalRecipientsInputValue] = useState<string>("");

    const comboId = "recipients-combo";
    const selectedListId = `${comboId}-selection`;

    const selectedListRef = useRef<HTMLUListElement>(null);
    const comboboxInputRef = useRef<HTMLInputElement>(null);

    const onInternalRecipientSelect: ComboboxProps["onOptionSelect"] = (_, data) =>
    {
        const newSelectedRecipients = [...data.selectedOptions].map(
            optionValue => orgUsers.find(user => user.id === optionValue)
        ).filter(Boolean)
            .filter(user => user.id !== appUser?.id) as AppUserDetailedDto[];

        setSelectedInternalParticipants(newSelectedRecipients);
        setInternalRecipientsInputValue("");

        if (setInternalParticipants)
        {
            setInternalParticipants(newSelectedRecipients);
        }
    };

    const onTagClick = (recipient: AppUserDetailedDto, index: number) =>
    {
        const updatedParticipants = selectedInternalRecipients.filter(r => r.id !== recipient.id);
        setSelectedInternalParticipants(updatedParticipants);

        if (setInternalParticipants)
        {
            setInternalParticipants(updatedParticipants);
        }

        const indexToFocus = index === 0 ? 1 : index - 1;
        const tagToFocus = selectedListRef.current?.querySelector(
            `#${comboId}-remove-${indexToFocus}`
        );
        if (tagToFocus)
        {
            (tagToFocus as HTMLButtonElement).focus();
        }
        else
        {
            comboboxInputRef.current?.focus();
        }
    };

    const onFocus = () =>
    {
        setInternalRecipientsInputValue("");
    };

    const onChange = (event: React.ChangeEvent<HTMLInputElement>) =>
    {
        setInternalRecipientsInputValue(event.target.value);
    };

    const filteredUsers = orgUsers
        .filter(user => user.id !== appUser?.id)
        .filter(user => !internalRecipientsInputValue ||
            user.email.toLowerCase().includes(internalRecipientsInputValue.toLowerCase()) ||
            user.person.firstName?.toLowerCase().includes(internalRecipientsInputValue.toLowerCase()) ||
            user.person.lastName?.toLowerCase().includes(internalRecipientsInputValue.toLowerCase()));


    return (
        <>
            <Divider alignContent="start">
                Participants from your organization
            </Divider>
            <Field>
                {isLoadingUsers ? (
                    <Spinner size="tiny" label="Loading users..."/>
                ) : (
                    <>
                        {selectedInternalRecipients.length > 0 && (
                            <ul
                                id={selectedListId}
                                ref={selectedListRef}
                                className={styles.myOrgSelectedList}>
                                <span id={`${comboId}-remove`} hidden>
                                    Remove
                                </span>
                                {selectedInternalRecipients.map((recipient, i) => (
                                    <li key={recipient.id}>
                                        <Button
                                            size="small"
                                            shape="circular"
                                            appearance="primary"
                                            icon={<Dismiss12Regular/>}
                                            iconPosition="after"
                                            onClick={() => onTagClick(recipient, i)}
                                            id={`${comboId}-remove-${i}`}
                                        >
                                            {`${recipient.person.firstName} ${recipient.person.lastName}`}
                                        </Button>
                                    </li>
                                ))}
                            </ul>
                        )}
                        <Combobox
                            id={"my-org-recipients-combobox"}
                            multiselect={true}
                            placeholder="Select additional participants"
                            value={internalRecipientsInputValue}
                            onChange={onChange}
                            onFocus={onFocus}
                            onOptionSelect={onInternalRecipientSelect}
                            ref={comboboxInputRef}
                            selectedOptions={selectedInternalRecipients.map(recipient => recipient.id || "")}
                        >
                            {filteredUsers.map(user => (
                                <Option key={user.id} value={user.id || ""}>
                                    {`${user.person.firstName} ${user.person.lastName} (${user.email})`}
                                </Option>
                            ))}
                        </Combobox>
                    </>
                )}
            </Field>
        </>
    );
};


export default MyOrgRecipients;