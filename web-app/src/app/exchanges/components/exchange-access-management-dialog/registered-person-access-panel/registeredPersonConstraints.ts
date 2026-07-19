import {ShareConstraints} from '../../../../../services/types/dtos.ts';
import {CONSTRAINED_ROLES, ExchangeShareRoleName} from '../../../../../services/types/roles.ts';

export type ConstraintTag = 'NO_DOWNLOAD' | 'NO_RESHARE' | 'WATERMARK' | 'REQUIRE_MFA';

export const CONSTRAINT_OPTIONS: Array<{value: ConstraintTag; label: string}> = [
    {value: 'NO_DOWNLOAD', label: 'No bulk document download'},
    {value: 'NO_RESHARE', label: 'No reshare'},
    {value: 'WATERMARK', label: 'Watermark'},
    {value: 'REQUIRE_MFA', label: 'Require MFA'},
];

export const isConstrainedRole = (roleName: ExchangeShareRoleName): boolean =>
    CONSTRAINED_ROLES.has(roleName);

export const constraintsFromTags = (tags: ConstraintTag[]): ShareConstraints => ({
    can_download: !tags.includes('NO_DOWNLOAD'),
    can_reshare: !tags.includes('NO_RESHARE'),
    watermark: tags.includes('WATERMARK'),
    require_mfa: tags.includes('REQUIRE_MFA'),
});

export const parseConstraintSelection = (selectedOptions: string[] | undefined): ConstraintTag[] =>
{
    const valid = new Set(CONSTRAINT_OPTIONS.map(option => option.value));
    return (selectedOptions ?? []).filter(
        (value): value is ConstraintTag => valid.has(value as ConstraintTag),
    );
};
