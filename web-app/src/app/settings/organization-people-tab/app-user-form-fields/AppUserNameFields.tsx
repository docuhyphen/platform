import {Field, Input} from "@fluentui/react-components";

export interface AppUserNameFieldIds
{
    firstNameField: string;
    firstNameInput: string;
    lastNameField: string;
    lastNameInput: string;
}

interface AppUserNameFieldsProps
{
    ids: AppUserNameFieldIds;
    firstName: string;
    lastName: string;
    onFirstNameChange: (value: string) => void;
    onLastNameChange: (value: string) => void;
    onEnter: () => void;
}

const AppUserNameFields = ({ids, firstName, lastName, onFirstNameChange, onLastNameChange, onEnter}: AppUserNameFieldsProps) => (
    <>
        <Field id={ids.firstNameField}
               label="First Name"
               required>
            <Input
                id={ids.firstNameInput}
                type="text"
                value={firstName}
                maxLength={50}
                onChange={(e) => onFirstNameChange(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") onEnter(); }}
            />
        </Field>

        <Field id={ids.lastNameField}
               label="Last Name"
               required>
            <Input
                id={ids.lastNameInput}
                type="text"
                value={lastName}
                maxLength={50}
                onChange={(e) => onLastNameChange(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") onEnter(); }}
            />
        </Field>
    </>
);

export default AppUserNameFields;
