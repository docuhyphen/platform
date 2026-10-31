import {useEffect, useState} from 'react';
import {ResponseError} from '../../../models/models';
import {saveExchangeFieldValues} from '../../../../services/fieldsService';
import {FieldValueEntryPayload} from './fieldValuePayload';

const VALUES_MOVED_ON = 'These values changed after this form loaded, so your save was not applied. '
    + 'The current values are loaded again; check your entries and save.';

const SAVE_FAILED = 'Failed to save values';

export interface FieldValuesSave
{
    saving: boolean;
    error: string | null;
    save: (entries: FieldValueEntryPayload[]) => Promise<void>;
}

const refusalMessage = (refusal: unknown): string =>
{
    if (typeof refusal === 'string') return refusal.trim() || SAVE_FAILED;
    const stated = (refusal as ResponseError | null)?.errorMessage;
    return stated?.trim() || SAVE_FAILED;
};

export const useFieldValuesSave = (
    exchangeId: string,
    servedETag: string,
    onValuesChanged: () => void,
): FieldValuesSave =>
{
    const [expectedETag, setExpectedETag] = useState(servedETag);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => setExpectedETag(servedETag), [servedETag]);

    const save = async (entries: FieldValueEntryPayload[]) =>
    {
        setSaving(true);
        setError(null);
        try
        {
            const result = await saveExchangeFieldValues(exchangeId, {values: entries}, expectedETag);
            if (result.outcome === 'STALE') setError(VALUES_MOVED_ON);
            else if (result.assignment.etag) setExpectedETag(result.assignment.etag);
            onValuesChanged();
        }
        catch (refusal: unknown)
        {
            setError(refusalMessage(refusal));
        }
        finally
        {
            setSaving(false);
        }
    };

    return {saving, error, save};
};
