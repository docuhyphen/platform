import {useEffect, useState} from 'react';
import {SystemVariableDto} from '../../../models/models';
import {getAvailableVariables} from '../../../../services/variableService';

export const useSystemVariables = () =>
{
    const [systemVars, setSystemVars] = useState<SystemVariableDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        setLoading(true);
        getAvailableVariables()
            .then(available => setSystemVars(available.system))
            .catch(() => setError('Failed to load system variables'))
            .finally(() => setLoading(false));
    }, []);

    return {systemVars, loading, error};
};
