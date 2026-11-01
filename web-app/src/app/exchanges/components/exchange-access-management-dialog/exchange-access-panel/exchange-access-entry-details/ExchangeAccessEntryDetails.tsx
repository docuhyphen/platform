import React from 'react';
import {Button, Text} from '@fluentui/react-components';
import {ExchangeAccessEntryDto} from '../../../../../../services/types/dtos';
import {ExchangeShareRoleName} from '../../../../../../services/types/roles';
import {AccessEntryDraft, AccessEntryState, ConstraintTag, labelsForTags} from '../accessEntryConstraints.ts';
import AccessConstraintTagPicker from '../access-constraint-tag-picker/AccessConstraintTagPicker.tsx';
import AccessEntryRoleControls from '../access-entry-role-controls/AccessEntryRoleControls.tsx';
import {useExchangeAccessEntryDetailsStyles} from './ExchangeAccessEntryDetailsStyles.tsx';

interface ExchangeAccessEntryDetailsProps
{
    entry: ExchangeAccessEntryDto;
    draft: AccessEntryDraft;
    state: AccessEntryState;
    busy: boolean;
    onRoleChange: (roleName: ExchangeShareRoleName) => void;
    onConstraintTagsChange: (tags: ConstraintTag[]) => void;
    onSave: () => void;
    onReinstate: () => void;
    onRevoke: () => void;
}

const ExchangeAccessEntryDetails: React.FC<ExchangeAccessEntryDetailsProps> = (props) =>
{
    const styles = useExchangeAccessEntryDetailsStyles();
    const {entry, draft, state, busy} = props;
    return (
        <div id={"exchange-access-panel-div-5"}
             className={styles.details}>

            <AccessEntryRoleControls
                shareId={entry.shareId}
                roleName={draft.roleName}
                busy={busy}
                isImmutable={state.isImmutable}
                isRevoked={state.isRevoked}
                onRoleChange={props.onRoleChange}
                onReinstate={props.onReinstate}
                onRevoke={props.onRevoke}
            />
            {entry.expiresAt && <Text
                id={"exchange-access-panel-text-6"}
                size={200}>Expires: {new Date(entry.expiresAt).toLocaleDateString()}</Text>}

            {state.isProtected && (
                <Text id={"exchange-access-panel-text-7"}
                      size={200}>This is your own access entry and cannot be changed or
                    revoked.</Text>
            )}

            {!state.isProtected && state.isOwner && (
                <Text id={"exchange-access-panel-text-8"}
                      size={200}>Exchange owner access is required and cannot be changed or
                    revoked.</Text>
            )}

            {state.constrained && !state.isImmutable && (
                <div id={"exchange-access-panel-div-7"}
                     className={styles.constraintsEditor}>
                    <AccessConstraintTagPicker
                        tags={state.tags}
                        disabled={busy}
                        onChange={props.onConstraintTagsChange}
                    />
                </div>
            )}

            {state.constrained && state.isImmutable && state.tags.length > 0 && (
                <div id={"exchange-access-panel-div-8"}
                     className={styles.constraintsEditor}>
                    <Text id={"exchange-access-panel-text-9"}
                          size={200}>Constraints: {labelsForTags(state.tags).join(', ')}</Text>
                </div>
            )}

            {!state.constrained && !state.isImmutable && (
                <Text id={"exchange-access-panel-text-10"}
                      size={200}>No additional constraints for this role.</Text>
            )}

            <div id={"exchange-access-panel-div-9"}
                 className={styles.accessSave}>

                {!state.isRevoked && !state.isImmutable && (
                    <Button
                        id={`access-entry-save-${entry.shareId}`}
                        className={styles.actionButton}
                        size="small"
                        appearance="secondary"
                        shape={"circular"}
                        disabled={busy || !draft.dirty}
                        onClick={props.onSave}
                    >
                        Save
                    </Button>
                )}
            </div>
        </div>
    );
};

export default ExchangeAccessEntryDetails;
