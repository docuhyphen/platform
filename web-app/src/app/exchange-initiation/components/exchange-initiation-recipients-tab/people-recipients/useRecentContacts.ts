import {useEffect, useState} from "react";
import {fetchRecentContacts, UserContactDto} from "../../../../../services/personalContactsApi";

export const useRecentContacts = () =>
{
    const [recents, setRecents] = useState<UserContactDto[]>([]);
    const [isLoadingRecents, setIsLoadingRecents] = useState<boolean>(false);

    useEffect(() =>
    {
        let cancelled = false;
        (async () =>
        {
            setIsLoadingRecents(true);
            try
            {
                const data = await fetchRecentContacts(6);
                if (!cancelled) setRecents(data ?? []);
            }
            catch (err)
            {
                console.error("Failed to load recent contacts:", err);
            }
            finally
            {
                if (!cancelled) setIsLoadingRecents(false);
            }
        })();
        return () => { cancelled = true; };
    }, []);

    return {recents, isLoadingRecents};
};
