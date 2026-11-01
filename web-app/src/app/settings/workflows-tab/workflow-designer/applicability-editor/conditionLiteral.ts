import {FieldValueType} from '../../../../models/models';
import {
    toCanonicalDateTime,
    toDateTimeInputValue,
} from '../../../../exchanges/components/exchange-fields-tab/fieldDateTimeCanonical';
import {toExactNumberText} from '../../../../exchanges/components/exchange-fields-tab/fieldNumberText';

export const toConditionLiteral = (valueType: FieldValueType, raw: string): unknown =>
{
    switch (valueType)
    {
        case FieldValueType.INTEGER:
        case FieldValueType.DECIMAL:
            return toExactNumberText(raw) ?? undefined;
        case FieldValueType.DATE_TIME:
            return toCanonicalDateTime(raw) ?? undefined;
        default:
            return raw;
    }
};

export const toConditionLiteralInputValue = (valueType: FieldValueType, literal: unknown): string =>
{
    if (valueType === FieldValueType.DATE_TIME) return toDateTimeInputValue(literal);

    return literal == null ? '' : String(literal);
};
