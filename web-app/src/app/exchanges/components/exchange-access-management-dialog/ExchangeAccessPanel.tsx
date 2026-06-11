import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
    Button, Card,
    Combobox,
    Field,
    Input,
    Option,
    Spinner,
    Tag,
    Text,
    Tooltip,
} from '@fluentui/react-components';
import {
    TagPicker,
    TagPickerControl,
    TagPickerGroup,
    TagPickerInput,
    TagPickerList,
    TagPickerOption,
} from '@fluentui/react-tag-picker';
import {ChevronDownRegular, ChevronUpRegular, DeleteRegular, PersonAddRegular} from '@fluentui/react-icons';
import {useExchangeAccessPanelStyles} from './ExchangeAccessPanelStyles';
import {useAuth} from '../../../../context/AuthContext';
import {
    changeExchangeAccessRole,
    grantExchangeAccess,
    listExchangeAccess,
    revokeExchangeAccess,
} from '../../../../services/exchangeApi';
import {ExchangeAccessEntryDto, ShareConstraints} from '../../../../services/types/dtos';
import {
    AssignableRoleDisplayNames,
    CONSTRAINED_ROLES,
    ExchangeShareRole,
    ExchangeShareRoleDisplayNames,
} from '../../../../services/types/roles';

interface ExchangeAccessPanelProps
{
    exchangeId: string;
}

type AccessEntryDraft = {
    roleName: string;
    constraints: ShareConstraints;
    dirty: boolean;
};

type ConstraintTag = 'NO_DOWNLOAD' | 'NO_RESHARE' | 'WATERMARK' | 'REQUIRE_MFA';

const CONSTRAINT_OPTIONS: Array<{ value: ConstraintTag; label: string }> = [
    {value: 'NO_DOWNLOAD', label: 'No bulk document download'},
    {value: 'NO_RESHARE', label: 'No reshare'},
    {value: 'WATERMARK', label: 'Watermark'},
    {value: 'REQUIRE_MFA', label: 'Require MFA'},
];

function parseConstraints(json?: string): ShareConstraints
{
    if (!json) return {};
    try
    {
        return JSON.parse(json) as ShareConstraints;
    }
    catch
    {
        return {};
    }
}

function isConstrainedRole(roleName: string): boolean
{
    return CONSTRAINED_ROLES.has(roleName as ExchangeShareRole);
}

function tagsFromConstraints(constraints: ShareConstraints): ConstraintTag[]
{
    const tags: ConstraintTag[] = [];
    if (constraints.can_download === false) tags.push('NO_DOWNLOAD');
    if (constraints.can_reshare === false) tags.push('NO_RESHARE');
    if (constraints.watermark) tags.push('WATERMARK');
    if (constraints.require_mfa) tags.push('REQUIRE_MFA');
    return tags;
}

function constraintsFromTags(tags: ConstraintTag[]): ShareConstraints
{
    return {
        can_download: !tags.includes('NO_DOWNLOAD'),
        can_reshare: !tags.includes('NO_RESHARE'),
        watermark: tags.includes('WATERMARK'),
        require_mfa: tags.includes('REQUIRE_MFA'),
    };
}

function mergeTagsInTheDocument(existing: ShareConstraints, tags: ConstraintTag[]): ShareConstraints
{
    const tagConstraints = constraintsFromTags(tags);
    return {
        ...existing,
        ...tagConstraints,
    };
}

function labelsForTags(tags: ConstraintTag[]): string[]
{
    const lookup = new Map(CONSTRAINT_OPTIONS.map((c) => [c.value, c.label]));
    return tags.map((tag) => lookup.get(tag) ?? tag);
}

function parseTagPickerSelection(selectedOptions: string[] | undefined): ConstraintTag[]
{
    if (!selectedOptions) return [];
    const valid = new Set(CONSTRAINT_OPTIONS.map((c) => c.value));
    return selectedOptions.filter((value): value is ConstraintTag => valid.has(value as ConstraintTag));
}

function serializeConstraintsForRole(roleName: string, constraints: ShareConstraints): string
{
    if (!isConstrainedRole(roleName))
    {
        return '{}';
    }
    return JSON.stringify(constraints ?? {});
}

function buildDraft(entry: ExchangeAccessEntryDto): AccessEntryDraft
{
    return {
        roleName: entry.roleName,
        constraints: parseConstraints(entry.constraintsJson),
        dirty: false,
    };
}

function buildDrafts(entries: ExchangeAccessEntryDto[]): Record<string, AccessEntryDraft>
{
    const next: Record<string, AccessEntryDraft> = {};
    entries.forEach((entry) =>
    {
        next[entry.shareId] = buildDraft(entry);
    });
    return next;
}

function entryDisplayLabel(entry: ExchangeAccessEntryDto): string
{
    if (entry.displayName?.trim()) return entry.displayName;
    if (entry.principalId?.includes('@')) return entry.principalId;
    return 'Unknown participant';
}

/**
 * An entry is "protected" (non-editable, non-revokable) when:
 *  - it is the caller's own OWNER share (structural), OR
 *  - it is any entry where the principalId matches the current user
 *    (you should not be able to modify/revoke your own access).
 */
function isProtectedEntry(entry: ExchangeAccessEntryDto, currentAppUserId?: string): boolean
{
    if (!currentAppUserId) return false;
    // Protect any entry that belongs to the current user
    if (entry.principalKind === 'USER' && entry.principalId === currentAppUserId) return true;
    return false;
}

function isOwnerEntry(entry: ExchangeAccessEntryDto): boolean
{
    return entry.roleName === 'OWNER';
}

const ExchangeAccessPanel: React.FC<ExchangeAccessPanelProps> = ({exchangeId}) =>
{
    const styles = useExchangeAccessPanelStyles();
    const {appUser} = useAuth();
    const [entries, setEntries] = useState<ExchangeAccessEntryDto[]>([]);
    const [drafts, setDrafts] = useState<Record<string, AccessEntryDraft>>({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    const [isAddFormOpen, setIsAddFormOpen] = useState(false);
    const [expandedEntryId, setExpandedEntryId] = useState<string | null>(null);

    const [newPersonEmail, setNewPersonEmail] = useState('');
    const [newRole, setNewRole] = useState<string>(ExchangeShareRole.VIEWER);
    const [newConstraintTags, setNewConstraintTags] = useState<ConstraintTag[]>([]);

    const loadAccess = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const data = await listExchangeAccess(exchangeId);
            setEntries(data);
            setDrafts(buildDrafts(data));
        }
        catch (err: unknown)
        {
            const message = err instanceof Error ? err.message : 'Failed to load access';
            setError(message);
        }
        finally
        {
            setLoading(false);
        }
    }, [exchangeId]);

    useEffect(() =>
    {
        loadAccess();
    }, [loadAccess]);

    const canAddConstrainedTags = useMemo(() => isConstrainedRole(newRole), [newRole]);

    const updateDraft = (shareId: string, updater: (prev: AccessEntryDraft) => AccessEntryDraft) =>
    {
        setDrafts((prev) =>
        {
            const current = prev[shareId];
            if (!current) return prev;
            return {
                ...prev,
                [shareId]: {...updater(current), dirty: true},
            };
        });
    };

    const persistEntries = (updated: ExchangeAccessEntryDto[]) =>
    {
        setEntries(updated);
        setDrafts(buildDrafts(updated));
    };

    const handleAddPerson = async () =>
    {
        if (!newPersonEmail.trim()) return;
        setBusy(true);
        setError(null);
        try
        {
            const constraintsJson = canAddConstrainedTags
                ? JSON.stringify(constraintsFromTags(newConstraintTags))
                : undefined;

            const result = await grantExchangeAccess(exchangeId, {
                principalKind: 'USER',
                principalId: newPersonEmail.trim(),
                roleName: newRole,
                constraintsJson,
            });
            persistEntries(result);
            setNewPersonEmail('');
            setNewRole(ExchangeShareRole.VIEWER);
            setNewConstraintTags([]);
            setIsAddFormOpen(false);
        }
        catch (err: unknown)
        {
            const apiError = err as Record<string, unknown> | undefined;
            const message = apiError?.message || apiError?.error || (err instanceof Error ? err.message : 'Failed to grant access');
            setError(String(message));
        }
        finally
        {
            setBusy(false);
        }
    };

    const handleSaveEntry = async (entry: ExchangeAccessEntryDto) =>
    {
        const draft = drafts[entry.shareId] ?? buildDraft(entry);
        setBusy(true);
        setError(null);
        try
        {
            const result = await changeExchangeAccessRole(exchangeId, entry.shareId, {
                roleName: draft.roleName,
                constraintsJson: serializeConstraintsForRole(draft.roleName, draft.constraints),
            });
            persistEntries(result);
        }
        catch (err: unknown)
        {
            const apiError = err as Record<string, unknown> | undefined;
            const message = apiError?.message || apiError?.error || (err instanceof Error ? err.message : 'Failed to save access entry');
            setError(String(message));
        }
        finally
        {
            setBusy(false);
        }
    };

    const handleReinstate = async (entry: ExchangeAccessEntryDto) =>
    {
        const draft = drafts[entry.shareId] ?? buildDraft(entry);
        setBusy(true);
        setError(null);
        try
        {
            const result = await grantExchangeAccess(exchangeId, {
                principalKind: entry.principalKind,
                principalId: entry.principalId,
                roleName: draft.roleName,
                constraintsJson: serializeConstraintsForRole(draft.roleName, draft.constraints),
            });
            persistEntries(result);
        }
        catch (err: unknown)
        {
            const apiError = err as Record<string, unknown> | undefined;
            const message = apiError?.message || apiError?.error || (err instanceof Error ? err.message : 'Failed to reinstate access');
            setError(String(message));
        }
        finally
        {
            setBusy(false);
        }
    };

    const handleRevoke = async (shareId: string) =>
    {
        setBusy(true);
        setError(null);
        try
        {
            const result = await revokeExchangeAccess(exchangeId, shareId);
            persistEntries(result);
        }
        catch (err: unknown)
        {
            const apiError = err as Record<string, unknown> | undefined;
            const message = apiError?.message || apiError?.error || (err instanceof Error ? err.message : 'Failed to revoke access');
            setError(String(message));
        }
        finally
        {
            setBusy(false);
        }
    };

    if (loading)
    {
        return <Spinner size="tiny" label="Loading access..."/>;
    }

    return (
        <div className={styles.container}>
            {error && <Text className={styles.error}>{error}</Text>}

            <div className={styles.toolbar}>
                <Text size={200}>Manage who has access and what they can do.</Text>
                <Button
                    size="small"
                    shape={"circular"}
                    appearance="primary"
                    icon={isAddFormOpen ? <DeleteRegular/> : <PersonAddRegular/>}
                    onClick={() => setIsAddFormOpen((open) => !open)}>
                    {isAddFormOpen ? 'Cancel' : 'Add person'}
                </Button>
            </div>

            <div className={styles.entries}>

                {isAddFormOpen && (
                    <div className={styles.addForm}>
                        <div  className={styles.addFormRow1}>
                            <Field label="Person email"
                                   className={styles.addFormPersonField}>
                                <Combobox
                                    placeholder="name@company.com"
                                    value={newPersonEmail}
                                    freeform
                                    onChange={(event) => setNewPersonEmail(event.target.value)}
                                />
                            </Field>
                            <Field label="Access role">
                            <Combobox
                                className={styles.roleField}
                                value={AssignableRoleDisplayNames[newRole] || ExchangeShareRoleDisplayNames[newRole as ExchangeShareRole] || newRole}
                                selectedOptions={[newRole]}
                                onOptionSelect={(_e, d) => setNewRole(d.optionValue || ExchangeShareRole.VIEWER)}
                            >
                                {Object.entries(AssignableRoleDisplayNames).map(([k, v]) => (
                                    <Option key={k} value={k}>{v}</Option>
                                ))}
                            </Combobox>
                        </Field>
                        </div>
                        {canAddConstrainedTags && (
                            <div className={`${styles.constraintsEditor} ${styles.addConstraintSection}`}>
                                <Field label="Access constraints">
                                    <TagPicker
                                        selectedOptions={newConstraintTags}
                                        onOptionSelect={(_e, data) => setNewConstraintTags(parseTagPickerSelection(data.selectedOptions))}
                                    >
                                        <TagPickerControl>
                                            <TagPickerGroup aria-label="Selected constraint tags">
                                                {newConstraintTags.map((tag) => (
                                                    <Tag key={tag} value={tag}
                                                         shape="rounded">{labelsForTags([tag])[0]}</Tag>
                                                ))}
                                            </TagPickerGroup>
                                            <TagPickerInput aria-label="Constraint tags"
                                                            placeholder="Add constraints"/>
                                        </TagPickerControl>
                                        <TagPickerList>
                                            {CONSTRAINT_OPTIONS
                                                .filter((option) => !newConstraintTags.includes(option.value))
                                                .map((option) => (
                                                    <TagPickerOption key={option.value} value={option.value}>
                                                        {option.label}
                                                    </TagPickerOption>
                                                ))}
                                        </TagPickerList>
                                    </TagPicker>
                                </Field>
                            </div>
                        )}

                        <div className={styles.accessSave}>
                            <Button
                                size="small"
                                appearance="primary"
                                disabled={busy || !newPersonEmail.trim()}
                                onClick={handleAddPerson}
                            >
                                Add
                            </Button>
                        </div>
                    </div>
                )}

                {entries.map((entry) =>
                {
                    const draft = drafts[entry.shareId] ?? buildDraft(entry);
                    const isRevoked = entry.status === 'REVOKED';
                    const showDetails = expandedEntryId === entry.shareId;
                    const tags = tagsFromConstraints(draft.constraints);
                    const constrained = isConstrainedRole(draft.roleName);
                    const isProtected = isProtectedEntry(entry, appUser?.id);
                    const isOwner = isOwnerEntry(entry);
                    // An entry is immutable if it belongs to the current user or is an OWNER role
                    const isImmutable = isProtected || isOwner;

                    return (
                        <Card key={entry.shareId} className={styles.row}>
                            <div className={styles.rowTop}>
                                <Button
                                    className={styles.actionButton}
                                    size="small"
                                    appearance="subtle"
                                    icon={showDetails ? <ChevronUpRegular/> : <ChevronDownRegular/>}
                                    onClick={() => setExpandedEntryId(showDetails ? null : entry.shareId)}>
                                </Button>

                                <Tooltip content={entryDisplayLabel(entry)} relationship="description">
                                    <Text className={styles.nameCell}
                                          weight="semibold">{entryDisplayLabel(entry)}</Text>
                                </Tooltip>

                                <Text size={200}>Granted: {new Date(entry.grantedAt).toLocaleString()}</Text>
                            </div>
                            {showDetails && (
                                <div className={styles.details}>

                                    <div className={styles.detailsRow1}>
                                        {isImmutable ? (
                                            <Text weight="semibold">
                                                {ExchangeShareRoleDisplayNames[draft.roleName as ExchangeShareRole] || draft.roleName}
                                            </Text>
                                        ) : (
                                            <Combobox
                                                value={ExchangeShareRoleDisplayNames[draft.roleName as ExchangeShareRole] || draft.roleName}
                                                selectedOptions={[draft.roleName]}
                                                disabled={busy}
                                                appearance={"filled-darker"}
                                                onOptionSelect={(_e, d) =>
                                                {
                                                    if (!d.optionValue) return;
                                                    updateDraft(entry.shareId, (prev) => ({
                                                        ...prev,
                                                        roleName: d.optionValue
                                                    }));
                                                }}
                                            >
                                                {Object.entries(AssignableRoleDisplayNames).map(([k, v]) => (
                                                    <Option key={k} value={k}>{v}</Option>
                                                ))}
                                            </Combobox>
                                        )}

                                        {isRevoked ? (
                                            <Button className={styles.actionButton}
                                                    size="small"
                                                    appearance="transparent"
                                                    disabled={busy || isImmutable}
                                                    onClick={() => handleReinstate(entry)}>
                                                Reinstate
                                            </Button>
                                        ) : (
                                            <Button
                                                className={styles.actionButton}
                                                size="small"
                                                appearance="transparent"
                                                icon={<DeleteRegular/>}
                                                disabled={busy || isImmutable}
                                                onClick={() => handleRevoke(entry.shareId)}>
                                                Revoke
                                            </Button>
                                        )}
                                    </div>
                                    {entry.expiresAt && <Text
                                        size={200}>Expires: {new Date(entry.expiresAt).toLocaleDateString()}</Text>}

                                    {isProtected && (
                                        <Text size={200}>This is your own access entry and cannot be changed or
                                            revoked.</Text>
                                    )}

                                    {!isProtected && isOwner && (
                                        <Text size={200}>Exchange owner access is required and cannot be changed or
                                            revoked.</Text>
                                    )}

                                    {constrained && !isImmutable && (
                                        <div className={styles.constraintsEditor}>
                                            <Field label="Access constraints">
                                                <TagPicker
                                                    selectedOptions={tags}
                                                    disabled={busy}
                                                    onOptionSelect={(_e, data) =>
                                                    {
                                                        const selected = parseTagPickerSelection(data.selectedOptions);
                                                        updateDraft(entry.shareId, (prev) => ({
                                                            ...prev,
                                                            constraints: mergeTagsInTheDocument(prev.constraints, selected),
                                                        }));
                                                    }}
                                                >
                                                    <TagPickerControl>
                                                        <TagPickerGroup aria-label="Selected constraint tags">
                                                            {tags.map((tag) => (
                                                                <Tag key={tag} value={tag}
                                                                     shape="rounded">{labelsForTags([tag])[0]}</Tag>
                                                            ))}
                                                        </TagPickerGroup>
                                                        <TagPickerInput aria-label="Constraint tags"/>
                                                    </TagPickerControl>
                                                    <TagPickerList>
                                                        {CONSTRAINT_OPTIONS
                                                            .filter((option) => !tags.includes(option.value))
                                                            .map((option) => (
                                                                <TagPickerOption key={option.value}
                                                                                 value={option.value}>
                                                                    {option.label}
                                                                </TagPickerOption>
                                                            ))}
                                                    </TagPickerList>
                                                </TagPicker>
                                            </Field>
                                            <Field label="Max views (blank = unlimited)"
                                                   className={styles.maxViewsField}>
                                                <Input
                                                    type="number"
                                                    appearance={"filled-darker"}
                                                    min={1}
                                                    value={draft.constraints.max_views?.toString() ?? ''}
                                                    onChange={(_e, data) =>
                                                    {
                                                        const parsed = Number.parseInt(data.value, 10);
                                                        updateDraft(entry.shareId, (prev) => ({
                                                            ...prev,
                                                            constraints: {
                                                                ...prev.constraints,
                                                                max_views: Number.isFinite(parsed) && parsed > 0 ? parsed : undefined,
                                                            },
                                                        }));
                                                    }}
                                                    disabled={busy}
                                                />
                                            </Field>
                                        </div>
                                    )}

                                    {constrained && isImmutable && tags.length > 0 && (
                                        <div className={styles.constraintsEditor}>
                                            <Text size={200}>Constraints: {labelsForTags(tags).join(', ')}</Text>
                                        </div>
                                    )}

                                    {!constrained && !isImmutable && (
                                        <Text size={200}>No additional constraints for this role.</Text>
                                    )}

                                    <div className={styles.accessSave}>

                                        {!isRevoked && !isImmutable && (
                                            <Button
                                                className={styles.actionButton}
                                                size="small"
                                                appearance="secondary"
                                                disabled={busy || !draft.dirty}
                                                onClick={() => handleSaveEntry(entry)}
                                            >
                                                Save
                                            </Button>
                                        )}
                                    </div>
                                </div>
                            )}
                        </Card>
                    );
                })}
            </div>

            {entries.length === 0 && <Text>No access entries yet.</Text>}
        </div>
    );
};

export default ExchangeAccessPanel;

