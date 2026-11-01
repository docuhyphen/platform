import {useRef, useState} from 'react';
import {searchContacts, UserContactDto} from '../../../services/personalContactsApi';

export interface GroupMemberSearch
{
    query: string;
    results: UserContactDto[];
    hasUnregisteredHits: boolean;
    selectedContacts: UserContactDto[];
    setSelectedContacts: (contacts: UserContactDto[]) => void;
    onQueryChange: (query: string) => void;
    reset: () => void;
}

export const useGroupMemberSearch = (): GroupMemberSearch =>
{
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<UserContactDto[]>([]);
    const [hasUnregisteredHits, setHasUnregisteredHits] = useState(false);
    const [selectedContacts, setSelectedContacts] = useState<UserContactDto[]>([]);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const reset = () =>
    {
        setSelectedContacts([]);
        setQuery('');
        setResults([]);
        setHasUnregisteredHits(false);
    };

    const onQueryChange = (nextQuery: string) =>
    {
        setQuery(nextQuery);
        if (debounceRef.current) clearTimeout(debounceRef.current);
        if (nextQuery.length < 2)
        {
            setResults([]);
            setHasUnregisteredHits(false);
            return;
        }
        debounceRef.current = setTimeout(async () =>
        {
            try
            {
                const contacts = await searchContacts(nextQuery, 10);
                const registered = contacts.filter(c => !!c.contactAppUserId);
                setResults(registered);
                setHasUnregisteredHits(contacts.length > registered.length);
            }
            catch
            {
                setResults([]);
                setHasUnregisteredHits(false);
            }
        }, 250);
    };

    return {query, results, hasUnregisteredHits, selectedContacts, setSelectedContacts, onQueryChange, reset};
};
