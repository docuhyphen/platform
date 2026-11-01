import {useEffect, useRef, useState} from "react";
import {searchContacts, UserContactDto} from "../../../../../services/personalContactsApi";

const SEARCH_DEBOUNCE_MS = 250;
const MIN_QUERY_LENGTH = 2;

export const useContactSearch = () =>
{
    const [query, setQuery] = useState<string>('');
    const [results, setResults] = useState<UserContactDto[]>([]);
    const [isSearching, setIsSearching] = useState<boolean>(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() =>
    {
        if (debounceRef.current) clearTimeout(debounceRef.current);
        const trimmed = query.trim();
        if (trimmed.length < MIN_QUERY_LENGTH)
        {
            setResults([]);
            setIsSearching(false);
            return;
        }
        setIsSearching(true);
        debounceRef.current = setTimeout(async () =>
        {
            try
            {
                const data = await searchContacts(trimmed, 10);
                setResults(data ?? []);
            }
            catch (err)
            {
                console.error("Failed to search contacts:", err);
                setResults([]);
            }
            finally
            {
                setIsSearching(false);
            }
        }, SEARCH_DEBOUNCE_MS);

        return () =>
        {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [query]);

    return {query, setQuery, results, isSearching};
};
