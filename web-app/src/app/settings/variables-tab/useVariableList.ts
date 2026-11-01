import {useEffect, useState} from 'react';
import {VariableDefinitionDto, VariableScope} from '../../models/models';
import {deleteVariable, listVariables} from '../../../services/variableService';

export const useVariableList = (scope: VariableScope, loadErrorMessage: string) =>
{
    const [variables, setVariables] = useState<VariableDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        listVariables(scope)
            .then(setVariables)
            .catch(() => setError(loadErrorMessage))
            .finally(() => setLoading(false));
    };

    useEffect(() =>
    {
        load();
    }, []);

    const remove = async (v: VariableDefinitionDto) =>
    {
        await deleteVariable(v.id).catch(() => null);
        load();
    };

    return {variables, loading, error, load, remove};
};
