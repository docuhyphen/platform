import React, {useRef, useState} from "react";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {Button, Combobox, ComboboxProps, Divider, Field, Option, Spinner} from "@fluentui/react-components";
import {Dismiss12Regular} from "@fluentui/react-icons";

const MyOrgRecipients: React.FC<{
    orgUsers: AppUserDetailedDto[];
    isLoadingUsers: boolean;
    selectedInternalRecipients: AppUserDetailedDto[];
    setSelectedInternalRecipients: (recipients: AppUserDetailedDto[]) => void;
    setInternalRecipients?: (users: AppUserDetailedDto[]) => void;
}> = ({
          orgUsers,
          isLoadingUsers,
          selectedInternalRecipients,
          setSelectedInternalRecipients,
          setInternalRecipients
      }) =>
{
    const [internalRecipientsInputValue, setInternalRecipientsInputValue] = useState<string>("");

    const comboId = "recipients-combo";
    const selectedListId = `${comboId}-selection`;

    const selectedListRef = useRef<HTMLUListElement>(null);
    const comboboxInputRef = useRef<HTMLInputElement>(null);

    const onInternalRecipientSelect: ComboboxProps["onOptionSelect"] = (_, data) =>
    {
        console.log("onInternalRecipientSelect", data)

        const newSelectedRecipients = [...data.selectedOptions].map(
            optionValue => orgUsers.find(user => user.id === optionValue)
        ).filter(Boolean) as AppUserDetailedDto[];

        console.log(newSelectedRecipients)

        setSelectedInternalRecipients(newSelectedRecipients);
        setInternalRecipientsInputValue("");

        if (setInternalRecipients)
        {
            setInternalRecipients(newSelectedRecipients);
        }
    };

    const onTagClick = (recipient: AppUserDetailedDto, index: number) =>
    {
        const updatedRecipients = selectedInternalRecipients.filter(r => r.id !== recipient.id);
        setSelectedInternalRecipients(updatedRecipients);

        if (setInternalRecipients)
        {
            setInternalRecipients(updatedRecipients);
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
        .filter(user => !internalRecipientsInputValue ||
            user.email.toLowerCase().includes(internalRecipientsInputValue.toLowerCase()) ||
            user.person.firstName?.toLowerCase().includes(internalRecipientsInputValue.toLowerCase()) ||
            user.person.lastName?.toLowerCase().includes(internalRecipientsInputValue.toLowerCase()));

    const labelledBy = selectedInternalRecipients.length > 0
        ? `${comboId} ${selectedListId}`
        : comboId;

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
                                style={{
                                    listStyleType: "none",
                                    marginBottom: "4px",
                                    marginTop: 0,
                                    paddingLeft: 0,
                                    display: "flex",
                                    gap: "4px",
                                    flexWrap: "wrap"
                                }}>
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
                                            aria-labelledby={`${comboId}-remove ${comboId}-remove-${i}`}
                                        >
                                            {`${recipient.person.firstName} ${recipient.person.lastName}`}
                                        </Button>
                                    </li>
                                ))}
                            </ul>
                        )}
                        <Combobox
                            aria-labelledby={labelledBy}
                            multiselect={true}
                            placeholder="Select additional recipients"
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