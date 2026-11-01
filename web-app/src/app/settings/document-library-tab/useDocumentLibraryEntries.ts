import {useEffect, useState} from 'react';
import {DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';
import {listDocumentLibraryEntries} from '../../../services/documentLibraryService.ts';
import {DocumentLibraryListTab} from './documentLibraryListTypes.ts';

export const useDocumentLibraryEntries = (activeTab: DocumentLibraryListTab) =>
{
    const [entries, setEntries] = useState<DocumentLibraryEntrySummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const loadEntries = () =>
    {
        setLoading(true);
        setError(null);
        listDocumentLibraryEntries({scope: activeTab})
            .then(setEntries)
            .catch(() => setError('Failed to load documents'))
            .finally(() => setLoading(false));
    };

    useEffect(() =>
    {
        loadEntries();
    }, [activeTab]);

    return {entries, setEntries, loading, error, loadEntries};
};
