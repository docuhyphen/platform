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
        return <Spinner id={"my-groups-recipients-spinner-1"}
                        size="small"
                        label="Loading personal groups..."/>;
    }

    const activeGroups = groups.filter(g => g.isActive);

    if (activeGroups.length === 0)
    {
        return (
            <Text id={"my-groups-recipients-text-1"}
                  size={200}
                  italic>
                No personal groups yet. Create one in <strong id={"my-groups-recipients-strong-1"}>Settings  to  My Groups</strong> and then come back here.
            </Text>
        );
    }

    return (
        <Field id={"my-groups-recipients-field-1"}
               label="Select a personal group">
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
                    <Option id={`my-groups-recipients-option-1-${g.id}`}
                            key={g.id}
                            value={g.id}
                            text={g.name}>
                        <div id={"my-groups-recipients-div-1"}
                             className={styles.myGroupsOptionContent}>
                            <Text id={"my-groups-recipients-text-2"}
                                  weight="semibold">{g.name}</Text>
                            {g.description && <Text id={"my-groups-recipients-text-3"}
                                                    size={200}>{g.description}</Text>}
                            <Text id={"my-groups-recipients-text-4"}
                                  size={200}>{g.members.filter(m => m.groupRole !== 'OWNER').length} member{g.members.filter(m => m.groupRole !== 'OWNER').length !== 1 ? 's' : ''}</Text>
                        </div>
                    </Option>
                ))}
            </Combobox>
        </Field>
    );
};

export default MyGroupsRecipients;

