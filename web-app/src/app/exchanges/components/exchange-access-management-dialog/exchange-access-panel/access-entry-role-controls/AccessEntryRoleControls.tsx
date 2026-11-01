import React from 'react';
import {Button, Combobox, Option, Text} from '@fluentui/react-components';
import {DeleteRegular} from '@fluentui/react-icons';
import {
    AssignableRoleDisplayNames,
    ExchangeShareRoleDisplayNames,
    ExchangeShareRoleName,
} from '../../../../../../services/types/roles';
import {useAccessEntryRoleControlsStyles} from './AccessEntryRoleControlsStyles.tsx';

interface AccessEntryRoleControlsProps
{
    shareId: string;
    roleName: ExchangeShareRoleName;
    busy: boolean;
    isImmutable: boolean;
    isRevoked: boolean;
    onRoleChange: (roleName: ExchangeShareRoleName) => void;
    onReinstate: () => void;
    onRevoke: () => void;
}

const AccessEntryRoleControls: React.FC<AccessEntryRoleControlsProps> = (props) =>
{
    const styles = useAccessEntryRoleControlsStyles();
    const roleLabel = ExchangeShareRoleDisplayNames[props.roleName as ExchangeShareRoleName] || props.roleName;
    return (
        <div id={"exchange-access-panel-div-6"}
             className={styles.detailsRow1}>
            {props.isImmutable ? (
                <Text id={"exchange-access-panel-text-5"}
                      weight="semibold">
                    {roleLabel}
                </Text>
            ) : (
                <Combobox
                    id={`combobox-access-entry-role-${props.shareId}`}
                    value={roleLabel}
                    selectedOptions={[props.roleName]}
                    disabled={props.busy}
                    appearance={"filled-darker"}
                    onOptionSelect={(_e, d) =>
                    {
                        if (!d.optionValue) return;
                        props.onRoleChange(d.optionValue as ExchangeShareRoleName);
                    }}
                >
                    {Object.entries(AssignableRoleDisplayNames).map(([k, v]) => (
                        <Option id={`exchange-access-panel-option-1-${k}`}
                                key={k}
                                value={k}>{v}</Option>
                    ))}
                </Combobox>
            )}

            {props.isRevoked ? (
                <Button
                    id={`access-entry-reinstate-${props.shareId}`}
                    className={styles.actionButton}
                    size="small"
                    appearance="transparent"
                    shape={"circular"}
                    disabled={props.busy || props.isImmutable}
                    onClick={props.onReinstate}>
                    Reinstate
                </Button>
            ) : (
                <Button
                    id={`access-entry-revoke-${props.shareId}`}
                    className={styles.actionButton}
                    size="small"
                    appearance="transparent"
                    shape={"circular"}
                    icon={<DeleteRegular/>}
                    disabled={props.busy || props.isImmutable}
                    onClick={props.onRevoke}>
                    Revoke
                </Button>
            )}
        </div>
    );
};

export default AccessEntryRoleControls;
