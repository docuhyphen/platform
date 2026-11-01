import {useCallback, useEffect, useState} from 'react';
import {CommunicationSummaryDto} from '../../models/models';
import {listCommunications} from '../../../services/communicationService';
import {CommunicationListTab} from './communicationListTypes';

export const useCommunicationsLoader = (activeTab: CommunicationListTab) =>
{
    const [communications, setCommunications] = useState<CommunicationSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const loadCommunications = useCallback(() =>
    {
        setLoading(true);
        setError(null);
        listCommunications({scope: activeTab})
            .then(setCommunications)
            .catch(() => setError('Failed to load communications'))
            .finally(() => setLoading(false));
    }, [activeTab]);

    useEffect(() => { loadCommunications(); }, [loadCommunications]);

    const clearCommunications = () => setCommunications([]);

    return {communications, loading, error, loadCommunications, clearCommunications};
};
