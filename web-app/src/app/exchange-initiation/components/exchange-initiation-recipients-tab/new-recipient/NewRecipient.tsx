import {Field, InfoLabel, Input} from "@fluentui/react-components";
import React from "react";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import {useNewRecipientStyles} from "./NewRecipientStyles.tsx";
import {useNewRecipient} from "./useNewRecipient.ts";

export interface ExchangeNewMainRecipient
{
    email: string;
    firstName: string;
    lastName: string;
}

interface NewRecipientProps
{
    isRequestingDocuments: boolean | null | undefined;
    setNewRecipient: (recipient: ExchangeNewMainRecipient) => void;
    newRecipient?: ExchangeNewMainRecipient;
    internalParticipants?: AppUserPublicDto[];
    setInternalParticipants?: (users: AppUserPublicDto[]) => void;
}

const NewRecipient: React.FC<NewRecipientProps> = props =>
{
    const styles = useNewRecipientStyles();
    const state = useNewRecipient(props);
    const updateFromInput = (field: keyof ExchangeNewMainRecipient, normalize: (value: string) => string) =>
        (event: React.ChangeEvent<HTMLInputElement>) =>
            state.updateRecipient(field, event.target.value ? normalize(event.target.value) : "");
    return (
        <>
            <Field
                id={"new-recipient-email-field"}
                label={
                    <InfoLabel info={"The email does not have to be from a registered user."}>
                        {props.isRequestingDocuments
                            ? "Email to request documents from"
                            : "Email to send documents to"}
                    </InfoLabel>
                }
            >
                <Input
                    id={"new-recipient-email-input"}
                    type={"email"}
                    value={state.recipient.email}
                    onChange={updateFromInput("email", value => value.trim().toLowerCase())}
                    placeholder={"Email"}
                />
            </Field>
            <div
                id={"new-recipient-name-fields"}
                className={styles.nameFields}
            >
                <Field
                    id={"new-recipient-first-name-field"}
                    className={styles.nameField}
                >
                    <Input
                        id={"new-recipient-first-name-input"}
                        type={"text"}
                        value={state.recipient.firstName}
                        onChange={updateFromInput("firstName", value => value.trim())}
                        placeholder={"First Name"}
                    />
                </Field>
                <Field
                    id={"new-recipient-last-name-field"}
                    className={styles.nameField}
                >
                    <Input
                        id={"new-recipient-last-name-input"}
                        type={"text"}
                        value={state.recipient.lastName}
                        onChange={updateFromInput("lastName", value => value.trim())}
                        placeholder={"Last Name"}
                    />
                </Field>
            </div>
            {state.appUserPersonOrganization && state.isRecipientDataValid && (
                <MyOrgRecipients
                    id={"new-recipient-internal-participants"}
                    orgUsers={state.orgUsers.filter(user => user.id !== state.appUser?.id)}
                    isLoadingUsers={state.isLoadingUsers}
                    selectedInternalRecipients={state.selectedInternalRecipients}
                    setSelectedInternalParticipants={state.setSelectedInternalParticipants}
                    setInternalParticipants={props.setInternalParticipants}
                />
            )}
        </>
    );
};

export default NewRecipient;
