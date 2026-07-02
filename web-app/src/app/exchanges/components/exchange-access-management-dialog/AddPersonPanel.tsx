import React, {useEffect, useMemo, useState} from 'react';
import {
    Button,
    Combobox,
    Field,
    MessageBar,
    MessageBarBody,
    Option,
    Spinner,
    Tag,
    Text,
} from '@fluentui/react-components';
import {useAddPersonPanelStyles} from './AddPersonPanelStyles';
import {
    TagPicker,
    TagPickerControl,
    TagPickerGroup,
    TagPickerInput,
    TagPickerList,
    TagPickerOption,
} from '@fluentui/react-tag-picker';
import {grantExchangeAccess} from '../../../../services/exchangeApi';
import {ShareConstraints} from '../../../../services/types/dtos';
import {
    AssignableRoleDisplayNames,
    CONSTRAINED_ROLES,
    ExchangeShareRoleName,
    ExchangeShareRoleDisplayNames,
} from '../../../../services/types/roles';
import {BackIcon} from '../../../components/IconBundles.tsx';
import {searchContacts, UserContactDto} from '../../../../services/personalContactsApi.ts';
import SinglePersonPicker from '../../../components/person-picker/single-person-picker/SinglePersonPicker.tsx';
import {PersonPickerItem} from '../../../components/person-picker/personPickerTypes.ts';

const toPersonPickerItem = (contact: UserContactDto): PersonPickerItem => ({
    id: contact.email,
    email: contact.email,
    firstName: contact.firstName,
    lastName: contact.lastName,
    avatarUrl: contact.avatarUrl,
});

interface Props {
    exchangeId: string;
    onBack: () => void;
    onPersonAdded: () => void;
}

type ConstraintTag = 'NO_DOWNLOAD' | 'NO_RESHARE' | 'WATERMARK' | 'REQUIRE_MFA';

const CONSTRAINT_OPTIONS: Array<{value: ConstraintTag; label: string}> = [
    {value: 'NO_DOWNLOAD', label: 'No bulk document download'},
    {value: 'NO_RESHARE', label: 'No reshare'},
    {value: 'WATERMARK', label: 'Watermark'},
    {value: 'REQUIRE_MFA', label: 'Require MFA'},
];

function isConstrainedRole(roleName: ExchangeShareRoleName): boolean {
    return CONSTRAINED_ROLES.has(roleName);
}

function constraintsFromTags(tags: ConstraintTag[]): ShareConstraints {
    return {
        can_download: !tags.includes('NO_DOWNLOAD'),
        can_reshare: !tags.includes('NO_RESHARE'),
        watermark: tags.includes('WATERMARK'),
        require_mfa: tags.includes('REQUIRE_MFA'),
    };
}

function labelsForTags(tags: ConstraintTag[]): string[] {
    const lookup = new Map(CONSTRAINT_OPTIONS.map(c => [c.value, c.label]));
    return tags.map(tag => lookup.get(tag) ?? tag);
}

function parseTagPickerSelection(selectedOptions: string[] | undefined): ConstraintTag[] {
    if (!selectedOptions) return [];
    const valid = new Set(CONSTRAINT_OPTIONS.map(c => c.value));
    return selectedOptions.filter((value): value is ConstraintTag => valid.has(value as ConstraintTag));
}

const AddPersonPanel: React.FC<Props> = ({exchangeId, onBack, onPersonAdded}) => {
    const styles = useAddPersonPanelStyles();

    const [email, setEmail] = useState('');
    const [role, setRole] = useState<ExchangeShareRoleName>(ExchangeShareRoleName.VIEWER);
    const [constraintTags, setConstraintTags] = useState<ConstraintTag[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [searchResults, setSearchResults] = useState<UserContactDto[]>([]);
    const [searching, setSearching] = useState(false);

    const canAddConstraints = useMemo(() => isConstrainedRole(role), [role]);

    useEffect(() =>
    {
        const query = email.trim();
        if (query.length < 2)
        {
            setSearchResults([]);
            setSearching(false);
            return;
        }

        let cancelled = false;
        setSearching(true);
        const handle = window.setTimeout(() =>
        {
            searchContacts(query, 10)
                .then(results =>
                {
                    if (!cancelled) setSearchResults(results);
                })
                .catch(() =>
                {
                    if (!cancelled) setSearchResults([]);
                })
                .finally(() =>
                {
                    if (!cancelled) setSearching(false);
                });
        }, 250);

        return () =>
        {
            cancelled = true;
            window.clearTimeout(handle);
        };
    }, [email]);

    const handleAdd = async () => {
        if (!email.trim()) return;
        setBusy(true);
        setError(null);
        try {
            const constraintsJson = canAddConstraints
                ? JSON.stringify(constraintsFromTags(constraintTags))
                : undefined;
            await grantExchangeAccess(exchangeId, {
                principalKind: 'USER',
                principalId: email.trim(),
                roleName: role,
                constraintsJson,
            });
            onPersonAdded();
        } catch (err: unknown) {
            const e = err as Record<string, unknown> | undefined;
            setError(String(e?.message || e?.error || (err instanceof Error ? err.message : 'Failed to grant access')));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className={styles.container}>
            <div className={styles.topBar}>
                <Button
                    id={"add-person-panel-back-btn"}
                    appearance="subtle"
                    shape="circular"
                    icon={<BackIcon/>}
                    onClick={onBack}
                    disabled={busy}
                >
                    Back
                </Button>
                <Text size={400} weight="semibold">Add person</Text>
            </div>

            {error && (
                <MessageBar intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            <div className={styles.form}>
                <Text size={200} className={styles.hint}>
                    Enter the person's email address and choose their access role.
                </Text>

                <div className={styles.row}>
                    <Field label="Person email" className={styles.personField}>
                        <SinglePersonPicker
                            id={"combobox-add-person-email"}
                            placeholder="name@company.com"
                            people={searchResults.map(toPersonPickerItem)}
                            query={email}
                            onQueryChange={setEmail}
                            onPersonSelect={person =>
                            {
                                if (person) setEmail(person.email);
                            }}
                            selectedPersonId={searchResults.some(contact => contact.email === email) ? email : null}
                            loading={searching}
                            freeform
                            disabled={busy}
                        />
                    </Field>
                    <Field label="Access role">
                        <Combobox
                            id={"combobox-add-person-role"}
                            value={AssignableRoleDisplayNames[role] || ExchangeShareRoleDisplayNames[role as ExchangeShareRoleName] || role}
                            selectedOptions={[role]}
                            disabled={busy}
                            onOptionSelect={(_e, d) => setRole(
                                (d.optionValue as ExchangeShareRoleName | undefined) ?? ExchangeShareRoleName.VIEWER,
                            )}
                        >
                            {Object.entries(AssignableRoleDisplayNames).map(([k, v]) => (
                                <Option key={k} value={k}>{v}</Option>
                            ))}
                        </Combobox>
                    </Field>
                </div>

                {canAddConstraints && (
                    <Field label="Access constraints">
                        <TagPicker
                            selectedOptions={constraintTags}
                            onOptionSelect={(_e, data) => setConstraintTags(parseTagPickerSelection(data.selectedOptions))}
                        >
                            <TagPickerControl>
                                <TagPickerGroup aria-label="Selected constraint tags">
                                    {constraintTags.map(tag => (
                                        <Tag key={tag} shape="circular" value={tag}>
                                            {labelsForTags([tag])[0]}
                                        </Tag>
                                    ))}
                                </TagPickerGroup>
                                <TagPickerInput aria-label="Constraint tags" placeholder="Add constraints"/>
                            </TagPickerControl>
                            <TagPickerList>
                                {CONSTRAINT_OPTIONS
                                    .filter(opt => !constraintTags.includes(opt.value))
                                    .map(opt => (
                                        <TagPickerOption key={opt.value} value={opt.value}>
                                            {opt.label}
                                        </TagPickerOption>
                                    ))}
                            </TagPickerList>
                        </TagPicker>
                    </Field>
                )}

                <div className={styles.actions}>
                    <Button
                        id={"add-person-panel-cancel-btn"}
                        appearance="secondary"
                        shape="circular"
                        onClick={onBack}
                        disabled={busy}
                    >
                        Cancel
                    </Button>
                    <Button
                        id={"add-person-panel-add-btn"}
                        appearance="primary"
                        shape="circular"
                        disabled={busy || !email.trim()}
                        onClick={handleAdd}
                    >
                        {busy ? <><Spinner size="tiny"/> Adding…</> : 'Add person'}
                    </Button>
                </div>
            </div>
        </div>
    );
};

export default AddPersonPanel;
