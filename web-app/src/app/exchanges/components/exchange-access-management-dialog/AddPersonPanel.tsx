import React, {useMemo, useState} from 'react';
import {
    Button,
    Combobox,
    Field,
    makeStyles,
    MessageBar,
    MessageBarBody,
    Option,
    Spinner,
    Tag,
    Text,
    tokens,
} from '@fluentui/react-components';
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
    ExchangeShareRole,
    ExchangeShareRoleDisplayNames,
} from '../../../../services/types/roles';
import {BackIcon} from '../../../components/IconBundles.tsx';

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

function isConstrainedRole(roleName: string): boolean {
    return CONSTRAINED_ROLES.has(roleName as ExchangeShareRole);
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

const useStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
    },
    topBar: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
    },
    form: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
    },
    row: {
        display: 'flex',
        gap: '8px',
    },
    personField: {
        flex: '1',
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: 'flex',
        justifyContent: 'flex-end',
        gap: '8px',
    },
});

const AddPersonPanel: React.FC<Props> = ({exchangeId, onBack, onPersonAdded}) => {
    const styles = useStyles();

    const [email, setEmail] = useState('');
    const [role, setRole] = useState<string>(ExchangeShareRole.VIEWER);
    const [constraintTags, setConstraintTags] = useState<ConstraintTag[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const canAddConstraints = useMemo(() => isConstrainedRole(role), [role]);

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
                        <Combobox
                            placeholder="name@company.com"
                            value={email}
                            freeform
                            disabled={busy}
                            onChange={e => setEmail(e.target.value)}
                        />
                    </Field>
                    <Field label="Access role">
                        <Combobox
                            value={AssignableRoleDisplayNames[role] || ExchangeShareRoleDisplayNames[role as ExchangeShareRole] || role}
                            selectedOptions={[role]}
                            disabled={busy}
                            onOptionSelect={(_e, d) => setRole(d.optionValue || ExchangeShareRole.VIEWER)}
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
                        appearance="secondary"
                        shape="circular"
                        onClick={onBack}
                        disabled={busy}
                    >
                        Cancel
                    </Button>
                    <Button
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
