import {useEffect, useState} from 'react';
import {SequenceDefinitionDto} from '../../models/models';
import {deleteSequence, listSequences, resetSequenceCounter} from '../../../services/variableService';

export const useOrganizationSequences = () =>
{
    const [sequences, setSequences] = useState<SequenceDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        listSequences()
            .then(setSequences)
            .catch(() => setError('Failed to load sequences'))
            .finally(() => setLoading(false));
    };

    useEffect(() =>
    {
        load();
    }, []);

    const remove = async (seq: SequenceDefinitionDto) =>
    {
        await deleteSequence(seq.id).catch((): null => null);
        load();
    };

    const resetCounter = async (seq: SequenceDefinitionDto) =>
    {
        await resetSequenceCounter(seq.id).catch((): null => null);
        load();
    };

    return {sequences, loading, error, load, remove, resetCounter};
};
