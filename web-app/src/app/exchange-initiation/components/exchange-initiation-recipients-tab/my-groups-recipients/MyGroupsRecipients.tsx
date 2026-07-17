import React, {useEffect, useState} from 'react';
import {Combobox, Field, Option, Spinner, Text} from '@fluentui/react-components';
import {fetchPersonalGroups} from '../../../../../services/meGroupsApi';
import {PrincipalGroupDto} from '../../../../../services/types/dtos';
import {OrganizationGroupBasicDto} from '../../../../../services/organizationApi';
import {useExchangeInitiationRecipientsTabStyles} from '../ExchangeInitiationRecipientsTabStyles.tsx';

interface MyGroupsRecipientsProps
{
    recipientOrgGroup: OrganizationGroupBasicDto | undefined;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
}

/**
 * Lets the user pick one of their personal groups as the exchange recipient.
 * The selected PrincipalGroupDto is cast to OrganizationGroupBasicDto so the
 * parent form can submit the selected personal group through the typed initiation contract.
 */
const MyGroupsRecipients: React.FC<MyGroupsRecipientsProps> = ({recipientOrgGroup, setRecipientOrgGroup}) =>
{
    const styles = useExchangeInitiationRecipientsTabStyles();
    const [groups, setGroups] = useState<PrincipalGroupDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [query, setQuery] = useState<string>('');

    useEffect(() =>
    {
        let cancelled = false;
        (async () =>
        {
            setLoading(true);
            try
            {
                const data = await fetchPersonalGroups();
                if (!cancelled) setGroups(data ?? []);
            }
            catch (err)
            {
                console.error('Failed to load personal groups', err);
            }
            finally
            {
                if (!cancelled) setLoading(false);
            }
        })();
        return () =>
        {
            cancelled = true;
        };
    }, []);

    // Keep combobox text in sync with external selection resets
    useEffect(() =>
    {
        if (!recipientOrgGroup)
        {
            setQuery('');
        }
        else
        {
            setQuery(recipientOrgGroup.name);
        }
    }, [recipientOrgGroup]);

    if (loading)
    {
        return <Spinner size="small" label="Loading personal groups..."/>;
    }

    const activeGroups = groups.filter(g => g.isActive);

    if (activeGroups.length === 0)
    {
        return (
            <Text size={200} italic>
                No personal groups yet. Create one in <strong>Settings  to  My Groups</strong> and then come back here.
            </Text>
        );
    }

    return (
        <Field label="Select a personal group">
            <Combobox
                id={"my-groups-recipient-combobox"}
                placeholder="Choose a group..."
                value={query}
                onChange={e => setQuery(e.target.value)}
                onOptionSelect={(_e, data) =>
                {
                    const selected = activeGroups.find(g => g.id === data.optionValue);
                    if (selected)
                    {
                        // Cast to OrganizationGroupBasicDto exch- only id and name are consumed downstream
                        setRecipientOrgGroup(selected as unknown as OrganizationGroupBasicDto);
                        setQuery(selected.name);
                    }
                    else
                    {
                        setRecipientOrgGroup(undefined);
                        setQuery('');
                    }
                }}
            >
                {activeGroups.map(g => (
                    <Option key={g.id} value={g.id} text={g.name}>
                        <div className={styles.myGroupsOptionContent}>
                            <Text weight="semibold">{g.name}</Text>
                            {g.description && <Text size={200}>{g.description}</Text>}
                            <Text size={200}>{g.members.filter(m => m.groupRole !== 'OWNER').length} member{g.members.filter(m => m.groupRole !== 'OWNER').length !== 1 ? 's' : ''}</Text>
                        </div>
                    </Option>
                ))}
            </Combobox>
        </Field>
    );
};

export default MyGroupsRecipients;


