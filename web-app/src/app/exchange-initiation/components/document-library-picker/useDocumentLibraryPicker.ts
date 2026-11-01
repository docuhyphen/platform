import {useEffect, useState} from 'react';
import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';
import {listDocumentLibraryEntries} from '../../../../services/documentLibraryService.ts';
import {PickerTab} from './documentLibraryPickerTypes.ts';

export const useDocumentLibraryPicker = (enforcedScope?: PickerTab) =>
{
    const [activeTab, setActiveTab] = useState<PickerTab>(enforcedScope ?? 'PERSONAL');
    const [entries, setEntries] = useState<DocumentLibraryEntrySummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

    useEffect(() =>
    {
        setSelectedIds(new Set());
        setLoading(true);
        setError(null);
        listDocumentLibraryEntries({scope: activeTab})
            .then(all =>
            {
                const visible = all.filter(e =>
                    e.hasFile && e.isActive && (e.scope === 'PERSONAL' || e.isPublished),
                );
                setEntries(visible);
            })
            .catch(() => setError('Failed to load documents'))
            .finally(() => setLoading(false));
    }, [activeTab, enforcedScope]);

    const toggleEntry = (id: string) =>
    {
        setSelectedIds(prev =>
        {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const selectedEntries = () => entries.filter(e => selectedIds.has(e.id));

    return {
        activeTab,
        setActiveTab,
        entries,
        loading,
        error,
        selectedIds,
        toggleEntry,
        selectedEntries,
    };
};
