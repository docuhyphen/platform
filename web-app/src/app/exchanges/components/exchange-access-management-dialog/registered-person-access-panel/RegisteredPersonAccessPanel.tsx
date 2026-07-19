import {Button, Combobox, Field, MessageBar, MessageBarBody, Option, Spinner, Text}
    from '@fluentui/react-components';
import SinglePersonPicker
    from '../../../../components/person-picker/single-person-picker/SinglePersonPicker.tsx';
import {PersonPickerItem} from '../../../../components/person-picker/personPickerTypes.ts';
import {
    AssignableRoleDisplayNames,
    ExchangeShareRoleName,
    ExchangeShareRoleDisplayNames,
} from '../../../../../services/types/roles.ts';
import RegisteredPersonConstraints from './RegisteredPersonConstraints.tsx';
import {useRegisteredPersonAccess} from './useRegisteredPersonAccess.ts';
import {useRegisteredPersonAccessPanelStyles} from './RegisteredPersonAccessPanelStyles.tsx';
import {UserContactDto} from '../../../../../services/personalContactsApi.ts';

interface RegisteredPersonAccessPanelProps
{
    exchangeId: string;
    onCancel: () => void;
    onAdded: () => void;
}

const toPickerItem = (contact: UserContactDto): PersonPickerItem => ({
    id: contact.email,
    email: contact.email,
    firstName: contact.firstName,
    lastName: contact.lastName,
    avatarUrl: contact.avatarUrl,
});

const RegisteredPersonAccessPanel = ({exchangeId, onCancel, onAdded}: RegisteredPersonAccessPanelProps) =>
{
    const styles = useRegisteredPersonAccessPanelStyles();
    const state = useRegisteredPersonAccess(exchangeId, onAdded);
    return (
        <div
            id={"registered-person-access-form"}
            className={styles.form}
        >
            <Text
                id={"registered-person-access-hint"}
                size={200}
                className={styles.hint}
            >
                Enter the person's email address and choose their access role.
            </Text>
            {state.error && <MessageBar
                id={"registered-person-access-error"}
                intent="error"
            >
                <MessageBarBody>{state.error}</MessageBarBody>
            </MessageBar>}
            <div
                id={"registered-person-access-fields"}
                className={styles.row}
            >
                <Field label="Person email">
                    <SinglePersonPicker
                        id={"combobox-add-person-email"}
                        placeholder="name@company.com"
                        people={state.contacts.map(toPickerItem)}
                        query={state.email}
                        onQueryChange={state.setEmail}
                        onPersonSelect={person => person && state.setEmail(person.email)}
                        selectedPersonId={state.contacts.some(contact => contact.email === state.email)
                            ? state.email : null}
                        loading={state.searching}
                        freeform
                        disabled={state.busy}
                    />
                </Field>
                <Field label="Access role">
                    <Combobox
                        id={"combobox-add-person-role"}
                        value={AssignableRoleDisplayNames[state.role]
                            ?? ExchangeShareRoleDisplayNames[state.role] ?? state.role}
                        selectedOptions={[state.role]}
                        disabled={state.busy}
                        onOptionSelect={(_event, data) => state.setRole(
                            (data.optionValue as ExchangeShareRoleName | undefined)
                            ?? ExchangeShareRoleName.VIEWER,
                        )}
                    >
                        {Object.entries(AssignableRoleDisplayNames).map(([value, label]) => (
                            <Option
                                id={`add-person-role-${value.toLowerCase()}`}
                                key={value}
                                value={value}
                            >
                                {label}
                            </Option>
                        ))}
                    </Combobox>
                </Field>
            </div>
            {state.canAddConstraints && <RegisteredPersonConstraints
                selected={state.constraints}
                onChange={state.setConstraints}
            />}
            <div
                id={"registered-person-access-actions"}
                className={styles.actions}
            >
                <Button
                    id={"add-person-panel-add-btn"}
                    appearance="primary"
                    shape="circular"
                    disabled={state.busy || !state.email.trim()}
                    onClick={state.add}
                >
                    {state.busy ? <><Spinner size="tiny"/> Adding</> : 'Add person'}
                </Button>
                <Button
                    id={"add-person-panel-cancel-btn"}
                    appearance="secondary"
                    shape="circular"
                    onClick={onCancel}
                    disabled={state.busy}
                >
                    Cancel
                </Button>
            </div>
        </div>
    );
};

export default RegisteredPersonAccessPanel;
