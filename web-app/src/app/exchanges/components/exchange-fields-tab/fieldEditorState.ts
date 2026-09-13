import {FieldValueDto, SchemaFieldBindingDto} from '../../../models/models';

/**
 * The value each binding of a schema currently holds on the server, keyed by the contract it answers.
 *
 * Every binding gets an entry, so an editor bound to one that has never been answered renders as
 * unanswered rather than as absent. A binding stored as empty starts from nothing for the same
 * reason: an empty reading is not a value a responder entered.
 */
export const storedFieldValues = (
    bindings: SchemaFieldBindingDto[],
    values: FieldValueDto[],
): Record<string, unknown> =>
{
    const stored: Record<string, unknown> = {};
    bindings.forEach(binding =>
    {
        const held = values.find(value => value.fieldContractId === binding.fieldContractId);
        stored[binding.fieldContractId] = held && !held.isEmpty ? held.value : undefined;
    });
    return stored;
};

/**
 * What each editor shows: the responder's own entry where they made one, and the stored value
 * everywhere else.
 *
 * Holding entries separately from stored values is what lets the form take a fresh reading of the
 * server without discarding work the responder has not saved yet. A binding they never touched
 * follows the server, so a save refused because the values moved on can be recovered from by
 * reloading rather than by asking them to enter everything again. A binding they cleared stays
 * cleared, because clearing is an entry.
 */
export const mergeFieldValueEdits = (
    stored: Record<string, unknown>,
    edits: Record<string, unknown>,
): Record<string, unknown> => ({...stored, ...edits});
