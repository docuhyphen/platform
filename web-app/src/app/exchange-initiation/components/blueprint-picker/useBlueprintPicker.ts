import {useEffect, useMemo, useState} from 'react';
import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {listBlueprints} from '../../../../services/blueprintService.ts';
import {useAuth} from '../../../../context/AuthContext.tsx';
import {PAGE_SIZE, PickerTab, SortOrder} from './blueprintPickerTypes.ts';

export const useBlueprintPicker = () =>
{
    const {appUserPersonOrganization} = useAuth();
    const hasOrganization = !!appUserPersonOrganization?.isActive;

    const [activeTab, setActiveTab] = useState<PickerTab>('PERSONAL');
    const [blueprints, setBlueprints] = useState<BlueprintDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [sortOrder, setSortOrder] = useState<SortOrder>('default');
    const [currentPage, setCurrentPage] = useState(0);

    const resetControls = () =>
    {
        setBlueprints([]);
        setSearchQuery('');
        setSelectedTags(new Set());
        setSortOrder('default');
        setCurrentPage(0);
    };

    useEffect(() =>
    {
        if (!hasOrganization && activeTab === 'ORG')
        {
            setActiveTab('PERSONAL');
            resetControls();
        }
    }, [activeTab, hasOrganization]);

    useEffect(() =>
    {
        setLoading(true);
        setError(null);
        listBlueprints({scope: activeTab})
            .then(all =>
            {
                const visible = all.filter(bp =>
                    bp.isActive && (bp.scope === 'PERSONAL' || bp.isPublished),
                );
                setBlueprints(visible);
            })
            .catch(() => setError('Failed to load blueprints'))
            .finally(() => setLoading(false));
    }, [activeTab]);

    const availableTags = useMemo(() =>
    {
        const tags = new Set<string>();
        blueprints.forEach(bp => bp.generalTags.forEach(t => tags.add(t)));
        return [...tags].sort();
    }, [blueprints]);

    const filteredBlueprints = useMemo(() =>
    {
        const q = searchQuery.trim().toLowerCase();
        let result = blueprints
            .filter(bp => !q || bp.name.toLowerCase().includes(q) || (bp.summary ?? '').toLowerCase().includes(q))
            .filter(bp => selectedTags.size === 0 || bp.generalTags.some(t => selectedTags.has(t)));
        if (sortOrder === 'nameAsc') result = [...result].sort((a, b) => a.name.localeCompare(b.name));
        else if (sortOrder === 'nameDesc') result = [...result].sort((a, b) => b.name.localeCompare(a.name));
        return result;
    }, [blueprints, searchQuery, selectedTags, sortOrder]);

    const totalPages = Math.ceil(filteredBlueprints.length / PAGE_SIZE);
    const visibleBlueprints = filteredBlueprints.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

    const selectTab = (tab: PickerTab) =>
    {
        setActiveTab(tab);
        resetControls();
    };

    const changeSearchQuery = (value: string) =>
    {
        setSearchQuery(value);
        setCurrentPage(0);
    };

    const toggleTag = (tag: string) =>
    {
        setSelectedTags(prev =>
        {
            const next = new Set(prev);
            if (next.has(tag)) next.delete(tag); else next.add(tag);
            return next;
        });
        setCurrentPage(0);
    };

    const dismissTag = (tag: string) =>
    {
        setSelectedTags(prev =>
        {
            const next = new Set(prev);
            next.delete(tag);
            return next;
        });
        setCurrentPage(0);
    };

    const clearTags = () =>
    {
        setSelectedTags(new Set());
        setCurrentPage(0);
    };

    const changeSortOrder = (order: SortOrder) =>
    {
        setSortOrder(order);
        setCurrentPage(0);
    };

    return {
        hasOrganization,
        activeTab,
        selectTab,
        blueprints,
        loading,
        error,
        searchQuery,
        changeSearchQuery,
        availableTags,
        selectedTags,
        toggleTag,
        dismissTag,
        clearTags,
        sortOrder,
        changeSortOrder,
        filteredBlueprints,
        visibleBlueprints,
        totalPages,
        currentPage,
        setCurrentPage,
    };
};
