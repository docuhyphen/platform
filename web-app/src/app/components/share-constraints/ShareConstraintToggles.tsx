import React from 'react';
import {Checkbox, Field, InfoLabel, Text} from '@fluentui/react-components';
import {ShareConstraints} from '../../../services/types/dtos';
import {useShareConstraintTogglesStyles} from "./ShareConstraintTogglesStyles.tsx";

interface ShareConstraintTogglesProps
{
    constraints: ShareConstraints;
    onChange: (constraints: ShareConstraints) => void;
    disabled?: boolean;
}

const ShareConstraintToggles: React.FC<ShareConstraintTogglesProps> = ({constraints, onChange, disabled}) =>
{
    const styles = useShareConstraintTogglesStyles();
    const update = (partial: Partial<ShareConstraints>) =>
    {
        onChange({...constraints, ...partial});
    };

    return (
        <div id={"share-constraint-toggles-div-1"}
             className={styles.container}>
            <Text id={"share-constraint-toggles-text-1"}
                  weight="semibold"
                  size={200}>Recipient Constraints</Text>
            <Field id={"share-constraint-toggles-field-1"}>
                <Checkbox
                    id={"share-constraint-can-download"}
                    label={
                        <InfoLabel id={"share-constraint-toggles-info-label-1"}
                                   info="Override the session's download setting for this recipient. When unchecked, this recipient cannot download any documents regardless of the exchange-level setting.">
                            Can download documents
                        </InfoLabel>
                    }
                    checked={constraints.can_download !== false}
                    disabled={disabled}
                    onChange={(_e, d) => update({can_download: !!d.checked})}
                />
            </Field>
            <Field id={"share-constraint-toggles-field-2"}>
                <Checkbox
                    id={"share-constraint-can-reshare"}
                    label="Can reshare session"
                    checked={constraints.can_reshare !== false}
                    disabled={disabled}
                    onChange={(_e, d) => update({can_reshare: !!d.checked})}
                />
            </Field>
            <Field id={"share-constraint-toggles-field-3"}>
                <Checkbox
                    id={"share-constraint-watermark"}
                    label="Apply watermark"
                    checked={!!constraints.watermark}
                    disabled={disabled}
                    onChange={(_e, d) => update({watermark: !!d.checked})}
                />
            </Field>
            <Field id={"share-constraint-toggles-field-4"}>
                <Checkbox
                    id={"share-constraint-require-mfa"}
                    label="Require MFA"
                    checked={!!constraints.require_mfa}
                    disabled={disabled}
                    onChange={(_e, d) => update({require_mfa: !!d.checked})}
                />
            </Field>
        </div>
    );
};

export default ShareConstraintToggles;
