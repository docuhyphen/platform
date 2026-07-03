import {FieldDataClassification, FieldValueType} from '../../models/models';

export const VALUE_TYPE_LABELS: Record<FieldValueType, string> = {
    [FieldValueType.SHORT_TEXT]: 'Short text',
    [FieldValueType.LONG_TEXT]: 'Long text',
    [FieldValueType.BOOLEAN]: 'Yes or No',
    [FieldValueType.INTEGER]: 'Integer',
    [FieldValueType.DECIMAL]: 'Decimal',
    [FieldValueType.DATE]: 'Date',
    [FieldValueType.DATE_TIME]: 'Date and time',
    [FieldValueType.SINGLE_SELECT]: 'Single selection',
    [FieldValueType.MULTI_SELECT]: 'Multiple selection',
};

export const CLASSIFICATION_LABELS: Record<FieldDataClassification, string> = {
    [FieldDataClassification.PUBLIC]: 'Public',
    [FieldDataClassification.INTERNAL]: 'Internal',
    [FieldDataClassification.CONFIDENTIAL]: 'Confidential',
    [FieldDataClassification.RESTRICTED]: 'Restricted',
};

export const SELECT_TYPES: FieldValueType[] = [
    FieldValueType.SINGLE_SELECT,
    FieldValueType.MULTI_SELECT,
];

export const typeSupportsOptions = (type: FieldValueType): boolean =>
    SELECT_TYPES.includes(type);
