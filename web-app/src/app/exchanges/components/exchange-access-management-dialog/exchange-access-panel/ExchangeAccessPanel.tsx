import React from 'react';
import {Button, Spinner, Text} from '@fluentui/react-components';
import {PersonAddRegular} from '@fluentui/react-icons';
import {useExchangeAccessPanelStyles} from './ExchangeAccessPanelStyles';
import {useAuth} from '../../../../../context/AuthContext';
import {useExchangeAccessEntries} from './useExchangeAccessEntries.ts';
import ExchangeAccessEntryCard from './exchange-access-entry-card/ExchangeAccessEntryCard.tsx';

interface ExchangeAccessPanelProps
{
    exchangeId: string;
    onAddPerson: () => void;
}

const ExchangeAccessPanel: React.FC<ExchangeAccessPanelProps> = ({exchangeId, onAddPerson}) =>
{
    const styles = useExchangeAccessPanelStyles();
    const {appUser} = useAuth();
    const access = useExchangeAccessEntries(exchangeId);

    if (access.loading)
    {
        return <Spinner id={"exchange-access-panel-spinner-1"}
                        size="tiny"
                        label="Loading access..."/>;
    }

    return (
        <div id={"exchange-access-panel-div-1"}
             className={styles.container}>
            {access.error && <Text id={"exchange-access-panel-text-1"}
                                   className={styles.error}>{access.error}</Text>}

            <div id={"exchange-access-panel-div-2"}
                 className={styles.toolbar}>
                <Text id={"exchange-access-panel-text-2"}
                      size={200}>Manage who has access and what they can do.</Text>
                <Button
                    id={"access-panel-add-person-btn"}
                    size="small"
                    shape="circular"
                    appearance="secondary"
                    icon={<PersonAddRegular/>}
                    onClick={onAddPerson}>
                    Add person
                </Button>
            </div>

            <div id={"exchange-access-panel-div-3"}
                 className={styles.entries}>

                {access.entries.map((entry) => (
                    <ExchangeAccessEntryCard
                        key={entry.shareId}
                        entry={entry}
                        draft={access.draftFor(entry)}
                        busy={access.busy}
                        expanded={access.expandedEntryId === entry.shareId}
                        currentAppUserId={appUser?.id}
                        onToggleExpanded={() => access.toggleExpanded(entry.shareId)}
                        onRoleChange={(roleName) => access.changeRole(entry.shareId, roleName)}
                        onConstraintTagsChange={(tags) => access.changeConstraintTags(entry.shareId, tags)}
                        onSave={() => access.saveEntry(entry)}
                        onReinstate={() => access.reinstateEntry(entry)}
                        onRevoke={() => access.revokeEntry(entry.shareId)}
                    />
                ))}
            </div>

            {access.entries.length === 0 && <Text id={"exchange-access-panel-text-11"}>No access entries yet.</Text>}
        </div>
    );
};

export default ExchangeAccessPanel;
