import {useEffect, useState} from 'react';
import {ResponseError} from '../../../models/models';
import {saveExchangeFieldValues} from '../../../../services/fieldsService';
import {FieldValueEntryPayload} from './fieldValuePayload';

/** What a responder is told when their save was refused because the values moved on beneath it. */
const VALUES_MOVED_ON = 'These values changed after this form loaded, so your save was not applied. '
    + 'The current values are loaded again; check your entries and save.';

/** What a responder is told when a refusal carried nothing that could be shown to them. */
const SAVE_FAILED = 'Failed to save values';

export interface FieldValuesSave
{
    /** Whether a save is in flight, so the form can hold its action. */
    saving: boolean;
    /** Why the last save did not store anything, or null where the last one did. */
    error: string | null;
    save: (entries: FieldValueEntryPayload[]) => Promise<void>;
}

/** The message a refusal states, or a plain one where the refusal states nothing readable. */
const refusalMessage = (refusal: unknown): string =>
{
    if (typeof refusal === 'string') return refusal.trim() || SAVE_FAILED;
    const stated = (refusal as ResponseError | null)?.errorMessage;
    return stated?.trim() || SAVE_FAILED;
};

/**
 * Saves an Exchange's field values against the version of them the form is showing.
 *
 * The validator served with the values is what a save states, and a successful save answers with the
 * validator of the version it produced, which the next save states in turn. Adopting it immediately
 * is what lets a responder save twice in a row without being refused for a version that is only
 * still in flight, while a validator newly served by a reload replaces it, because the server has
 * then said something more recent than the form knows.
 *
 * A save refused because the values moved on is not retried. The stored values are reloaded and the
 * responder is told their save was not applied, because retrying without looking is exactly the
 * overwrite that stating a version exists to prevent.
 */
export const useFieldValuesSave = (
    exchangeId: string,
    servedETag: string | undefined,
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
            else setExpectedETag(result.assignment.etag);
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
