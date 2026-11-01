import {useEffect, useRef, useState} from 'react';
import {BlueprintDefinitionSummaryDto} from '../../models/models.tsx';
import {listBlueprints} from '../../../services/blueprintService.ts';
import {BlueprintListTab} from './blueprintListTypes.ts';

export interface BlueprintsLoader
{
    blueprints: BlueprintDefinitionSummaryDto[];
    loading: boolean;
    error: string | null;
    reload: () => void;
    clear: () => void;
}

export const useBlueprintsLoader = (activeTab: BlueprintListTab): BlueprintsLoader =>
{
    const [blueprints, setBlueprints] = useState<BlueprintDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const requestSeq = useRef(0);

    const reload = () =>
    {
        const seq = ++requestSeq.current;
        setLoading(true);
        setError(null);
        listBlueprints({scope: activeTab})
            .then(data => { if (seq === requestSeq.current) setBlueprints(data); })
            .catch(() => { if (seq === requestSeq.current) setError('Failed to load blueprints'); })
            .finally(() => { if (seq === requestSeq.current) setLoading(false); });
    };

    useEffect(() => { reload(); }, [activeTab]);

    return {blueprints, loading, error, reload, clear: () => setBlueprints([])};
};
