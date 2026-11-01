import {CreateSequenceRequest, SequenceResetPeriod, UpdateSequenceRequest} from '../../models/models';

export const RESET_PERIODS: SequenceResetPeriod[] = ['NEVER', 'YEARLY', 'MONTHLY'];

export const defaultSequenceForm = (): CreateSequenceRequest => ({
    name: '',
    key: '',
    padWidth: 0,
    prefix: '',
    suffix: '',
    resetPeriod: 'NEVER',
});

export const formatSequencePreview = (form: CreateSequenceRequest | UpdateSequenceRequest, currentValue = 0): string =>
{
    const next = currentValue + 1;
    const padWidth = (form as CreateSequenceRequest).padWidth ?? 0;
    const padded = padWidth > 0 ? String(next).padStart(padWidth, '0') : String(next);
    return `${(form as CreateSequenceRequest).prefix ?? ''}${padded}${(form as CreateSequenceRequest).suffix ?? ''}`;
};
