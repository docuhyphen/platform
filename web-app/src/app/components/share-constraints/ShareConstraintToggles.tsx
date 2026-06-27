import React from 'react';
import {Checkbox, Field, InfoLabel, Input, Text} from '@fluentui/react-components';
import {ShareConstraints} from '../../../services/types/dtos';
import {useShareConstraintTogglesStyles} from "./ShareConstraintTogglesStyles.tsx";

interface ShareConstraintTogglesProps
{
    constraints: ShareConstraints;
    onChange: (constraints: ShareConstraints) => void;
    disabled?: boolean;
}

/**
 * Toggles for participant-level constraints.
 * Used when granting/editing access with PARTICIPANT or VIEWER role.
 */
const ShareConstraintToggles: React.FC<ShareConstraintTogglesProps> = ({constraints, onChange, disabled}) =>
{
    const styles = useShareConstraintTogglesStyles();
    const update = (partial: Partial<ShareConstraints>) =>
    {
        onChange({...constraints, ...partial});
    };

    return (
        <div className={styles.container}>
            <Text weight="semibold" size={200}>Recipient Constraints</Text>
            <Field>
                <Checkbox
                    id={"share-constraint-can-download"}
                    label={
                        <InfoLabel info="Override the session's download setting for this recipient. When unchecked, this recipient cannot download any documents regardless of the exchange-level setting.">
                            Can download documents
                        </InfoLabel>
                    }
                    checked={constraints.can_download !== false}
                    disabled={disabled}
                    onChange={(_e, d) => update({can_download: !!d.checked})}
                />
            </Field>
            <Field>
                <Checkbox
                    id={"share-constraint-can-reshare"}
                    label="Can reshare session"
                    checked={constraints.can_reshare !== false}
                    disabled={disabled}
                    onChange={(_e, d) => update({can_reshare: !!d.checked})}
                />
            </Field>
            <Field>
                <Checkbox
                    id={"share-constraint-watermark"}
                    label="Apply watermark"
                    checked={!!constraints.watermark}
                    disabled={disabled}
                    onChange={(_e, d) => update({watermark: !!d.checked})}
                />
            </Field>
            <Field>
                <Checkbox
                    id={"share-constraint-require-mfa"}
                    label="Require MFA"
                    checked={!!constraints.require_mfa}
                    disabled={disabled}
                    onChange={(_e, d) => update({require_mfa: !!d.checked})}
                />
            </Field>
            <Field label="Max views (0 = unlimited)">
                <Input
                    id={"share-constraint-max-views"}
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
