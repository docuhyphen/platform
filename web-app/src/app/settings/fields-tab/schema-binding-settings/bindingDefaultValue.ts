import {FieldValueType} from '../../../models/models';
import {isFieldValueEmpty, toCanonicalValue,} from '../../../exchanges/components/exchange-fields-tab/fieldValueUtils';

export const defaultValueOf = (defaultValueJson?: string): unknown => {
    if (!defaultValueJson) return undefined;
    try {
        return JSON.parse(defaultValueJson);
    } catch {
        return undefined;
    }
};

export const defaultValueJsonFor = (valueType: FieldValueType, value: unknown): string | undefined => {
    const canonical = toCanonicalValue(valueType, value);
    return isFieldValueEmpty(canonical) ? undefined : JSON.stringify(canonical);
};
