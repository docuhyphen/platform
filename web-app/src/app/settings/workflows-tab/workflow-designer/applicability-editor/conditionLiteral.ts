import {FieldValueType} from '../../../../models/models';
import {
    toCanonicalDateTime,
    toDateTimeInputValue,
} from '../../../../exchanges/components/exchange-fields-tab/fieldDateTimeCanonical';
import {toExactNumberText} from '../../../../exchanges/components/exchange-fields-tab/fieldNumberText';

/**
 * A condition literal is compared against a stored answer, so it has to be written the same way one
 * is. A number keeps its digits instead of passing through a JavaScript number, and a date-time
 * names the moment it means rather than a wall-clock reading the server would have to guess at.
 */
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

/** A stored literal as the text its own input control can display. */
export const toConditionLiteralInputValue = (valueType: FieldValueType, literal: unknown): string =>
{
    if (valueType === FieldValueType.DATE_TIME) return toDateTimeInputValue(literal);

    return literal == null ? '' : String(literal);
};
