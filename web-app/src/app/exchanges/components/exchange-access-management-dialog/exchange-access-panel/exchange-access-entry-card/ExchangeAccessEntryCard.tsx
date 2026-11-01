import React from 'react';
import {Badge, Button, Card, Text, Tooltip} from '@fluentui/react-components';
import {ChevronDownRegular, ChevronUpRegular} from '@fluentui/react-icons';
import {ExchangeAccessEntryDto} from '../../../../../../services/types/dtos';
import {ExchangeShareRoleName} from '../../../../../../services/types/roles';
import {AccessEntryDraft, ConstraintTag, describeAccessEntry, entryDisplayLabel} from '../accessEntryConstraints.ts';
import ExchangeAccessEntryDetails from '../exchange-access-entry-details/ExchangeAccessEntryDetails.tsx';
import {useExchangeAccessEntryCardStyles} from './ExchangeAccessEntryCardStyles.tsx';

interface ExchangeAccessEntryCardProps
{
    entry: ExchangeAccessEntryDto;
    draft: AccessEntryDraft;
    busy: boolean;
    expanded: boolean;
    currentAppUserId?: string;
    onToggleExpanded: () => void;
    onRoleChange: (roleName: ExchangeShareRoleName) => void;
    onConstraintTagsChange: (tags: ConstraintTag[]) => void;
    onSave: () => void;
    onReinstate: () => void;
    onRevoke: () => void;
}

const ExchangeAccessEntryCard: React.FC<ExchangeAccessEntryCardProps> = (props) =>
{
    const styles = useExchangeAccessEntryCardStyles();
    const {entry, draft, expanded} = props;
    const state = describeAccessEntry(entry, draft, props.currentAppUserId);
    return (
        <Card id={`exchange-access-panel-card-1-${entry.shareId}`}
              className={styles.row}>
            <div id={"exchange-access-panel-div-4"}
                 className={styles.rowTop}>
                <Button
                    id={`access-entry-toggle-${entry.shareId}`}
                    className={styles.actionButton}
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={expanded ? <ChevronUpRegular/> : <ChevronDownRegular/>}
                    onClick={props.onToggleExpanded}>
                </Button>

                <Tooltip content={entryDisplayLabel(entry)}
                         relationship="description">
                    <Text id={"exchange-access-panel-text-3"}
                          className={styles.nameCell}
                          weight="semibold">{entryDisplayLabel(entry)}
                    </Text>
                </Tooltip>
                {state.isOwner && (<Badge id={"exchange-access-panel-badge-1"}
                                          appearance={"outline"}>You</Badge>)}
                <Text id={"exchange-access-panel-text-4"}
                      size={200}>Granted: {new Date(entry.grantedAt).toLocaleString()}</Text>
            </div>
            {expanded && (
                <ExchangeAccessEntryDetails
                    entry={entry}
                    draft={draft}
                    state={state}
                    busy={props.busy}
                    onRoleChange={props.onRoleChange}
                    onConstraintTagsChange={props.onConstraintTagsChange}
                    onSave={props.onSave}
                    onReinstate={props.onReinstate}
                    onRevoke={props.onRevoke}
                />
            )}
        </Card>
    );
};

export default ExchangeAccessEntryCard;
