import {ExchangeAccessEntryDto, ShareConstraints} from '../../../../../services/types/dtos';
import {CONSTRAINED_ROLES, ExchangeShareRoleName} from '../../../../../services/types/roles';

export type AccessEntryDraft = {
    roleName: ExchangeShareRoleName;
    constraints: ShareConstraints;
    dirty: boolean;
};

export type ConstraintTag = 'NO_DOWNLOAD' | 'NO_RESHARE' | 'WATERMARK' | 'REQUIRE_MFA';

export type AccessEntryState = {
    isRevoked: boolean;
    isProtected: boolean;
    isOwner: boolean;
    isImmutable: boolean;
    constrained: boolean;
    tags: ConstraintTag[];
};

export const CONSTRAINT_OPTIONS: Array<{ value: ConstraintTag; label: string }> = [
    {value: 'NO_DOWNLOAD', label: 'No bulk document download'},
    {value: 'NO_RESHARE', label: 'No reshare'},
    {value: 'WATERMARK', label: 'Watermark'},
    {value: 'REQUIRE_MFA', label: 'Require MFA'},
];

export function parseConstraints(json?: string): ShareConstraints
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

export function isConstrainedRole(roleName: ExchangeShareRoleName): boolean
{
    return CONSTRAINED_ROLES.has(roleName);
}

export function tagsFromConstraints(constraints: ShareConstraints): ConstraintTag[]
{
    const tags: ConstraintTag[] = [];
    if (constraints.can_download === false) tags.push('NO_DOWNLOAD');
    if (constraints.can_reshare === false) tags.push('NO_RESHARE');
    if (constraints.watermark) tags.push('WATERMARK');
    if (constraints.require_mfa) tags.push('REQUIRE_MFA');
    return tags;
}

export function constraintsFromTags(tags: ConstraintTag[]): ShareConstraints
{
    return {
        can_download: !tags.includes('NO_DOWNLOAD'),
        can_reshare: !tags.includes('NO_RESHARE'),
        watermark: tags.includes('WATERMARK'),
        require_mfa: tags.includes('REQUIRE_MFA'),
    };
}

export function mergeTagsInTheDocument(existing: ShareConstraints, tags: ConstraintTag[]): ShareConstraints
{
    const tagConstraints = constraintsFromTags(tags);
    return {
        ...existing,
        ...tagConstraints,
    };
}

export function labelsForTags(tags: ConstraintTag[]): string[]
{
    const lookup = new Map(CONSTRAINT_OPTIONS.map((c) => [c.value, c.label]));
    return tags.map((tag) => lookup.get(tag) ?? tag);
}

export function parseTagPickerSelection(selectedOptions: string[] | undefined): ConstraintTag[]
{
    if (!selectedOptions) return [];
    const valid = new Set(CONSTRAINT_OPTIONS.map((c) => c.value));
    return selectedOptions.filter((value): value is ConstraintTag => valid.has(value as ConstraintTag));
}

export function serializeConstraintsForRole(roleName: ExchangeShareRoleName, constraints: ShareConstraints): string
{
    if (!isConstrainedRole(roleName))
    {
        return '{}';
    }
    return JSON.stringify(constraints ?? {});
}

export function buildDraft(entry: ExchangeAccessEntryDto): AccessEntryDraft
{
    return {
        roleName: entry.roleName,
        constraints: parseConstraints(entry.constraintsJson),
        dirty: false,
    };
}

export function buildDrafts(entries: ExchangeAccessEntryDto[]): Record<string, AccessEntryDraft>
{
    const next: Record<string, AccessEntryDraft> = {};
    entries.forEach((entry) =>
    {
        next[entry.shareId] = buildDraft(entry);
    });
    return next;
}

export function entryDisplayLabel(entry: ExchangeAccessEntryDto): string
{
    if (entry.displayName?.trim()) return entry.displayName;
    if (entry.principalId?.includes('@')) return entry.principalId;
    return 'Unknown participant';
}

export function isProtectedEntry(entry: ExchangeAccessEntryDto, currentAppUserId?: string): boolean
{
    if (!currentAppUserId) return false;
    if (entry.principalKind === 'USER' && entry.principalId === currentAppUserId) return true;
    return false;
}

export function isOwnerEntry(entry: ExchangeAccessEntryDto): boolean
{
    return entry.roleName === 'OWNER';
}

export function describeAccessEntry(
    entry: ExchangeAccessEntryDto,
    draft: AccessEntryDraft,
    currentAppUserId?: string,
): AccessEntryState
{
    const isProtected = isProtectedEntry(entry, currentAppUserId);
    const isOwner = isOwnerEntry(entry);
    return {
        isRevoked: entry.status === 'REVOKED',
        isProtected,
        isOwner,
        isImmutable: isProtected || isOwner,
        constrained: isConstrainedRole(draft.roleName),
        tags: tagsFromConstraints(draft.constraints),
    };
}
