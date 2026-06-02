import React from 'react';
import {Checkbox, Field, Input, Text} from '@fluentui/react-components';
import {ShareConstraints} from '../../../services/types/dtos';

interface ShareConstraintTogglesProps
{
    constraints: ShareConstraints;
    onChange: (constraints: ShareConstraints) => void;
    disabled?: boolean;
}

/**
 * Toggles for participant-level constraints (Plan 01).
 * Used when granting/editing access with PARTICIPANT or VIEWER role.
 */
const ShareConstraintToggles: React.FC<ShareConstraintTogglesProps> = ({constraints, onChange, disabled}) =>
{
    const update = (partial: Partial<ShareConstraints>) =>
    {
        onChange({...constraints, ...partial});
    };

    return (
        <div style={{display: 'flex', flexDirection: 'column', gap: 6}}>
            <Text weight="semibold" size={200}>Access Constraints</Text>
            <Field>
                <Checkbox
                    label="Allow download"
                    checked={constraints.can_download !== false}
                    disabled={disabled}
                    onChange={(_e, d) => update({can_download: !!d.checked})}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Allow reshare"
                    checked={constraints.can_reshare !== false}
                    disabled={disabled}
                    onChange={(_e, d) => update({can_reshare: !!d.checked})}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Watermark"
                    checked={!!constraints.watermark}
                    disabled={disabled}
                    onChange={(_e, d) => update({watermark: !!d.checked})}
                />
            </Field>
            <Field>
                <Checkbox
                    label="Require MFA"
                    checked={!!constraints.require_mfa}
                    disabled={disabled}
                    onChange={(_e, d) => update({require_mfa: !!d.checked})}
                />
            </Field>
            <Field label="Max views (0 = unlimited)">
                <Input
                    type="number"
                    size="small"
                    disabled={disabled}
                    value={constraints.max_views?.toString() || ''}
                    onChange={(_e, d) =>
                    {
                        const val = parseInt(d.value, 10);
                        update({max_views: isNaN(val) || val <= 0 ? undefined : val});
                    }}
                />
            </Field>
        </div>
    );
};

export default ShareConstraintToggles;
