import {WorkflowSubjectFieldDto} from "../../../../models/models.tsx";

export interface ConditionOperator
{
    value: string;
    label: string;
}

export const CONDITION_OPERATORS: readonly ConditionOperator[] = [
    {value: "==", label: "is equal to"},
    {value: "!=", label: "is not equal to"},
    {value: "contains", label: "contains"},
    {value: "startsWith", label: "starts with"},
    {value: ">", label: "is greater than"},
    {value: "<", label: "is less than"},
    {value: ">=", label: "is greater than or equal to"},
    {value: "<=", label: "is less than or equal to"},
];

export interface ParsedConditionExpression
{
    field: string;
    operator: string;
    value: string;
}

export function parseConditionExpression(expression: string): ParsedConditionExpression
{
    const match = expression.trim().match(
        /^\$subject\.([A-Za-z_][A-Za-z0-9_]*)\s+(startsWith|contains|>=|<=|==|!=|>|<)\s+(.+)$/,
    );
    if (!match) return {field: "", operator: "==", value: ""};
    const rawValue = match[3].trim();
    const value = rawValue.startsWith("'") && rawValue.endsWith("'")
        ? unescapeQuotedValue(rawValue.slice(1, -1))
        : rawValue;
    return {field: match[1], operator: match[2], value};
}

export function buildConditionExpression(
    field: string,
    operator: string,
    value: string,
    quoted: boolean,
): string | undefined
{
    if (!field || value === "") return undefined;
    const escapedValue = value.replace(/\\/g, "\\\\").replace(/'/g, "\\'");
    const operand = quoted ? `'${escapedValue}'` : value;
    return `$subject.${field} ${operator} ${operand}`;
}

export function getAllowedConditionOperators(
    field: WorkflowSubjectFieldDto | undefined,
): readonly ConditionOperator[]
{
    if (!field) return CONDITION_OPERATORS;
    const enumLike = parseEnumDescription(field.description) !== null;
    if (field.type === "UUID" || enumLike || field.type === "boolean")
    {
        return CONDITION_OPERATORS.filter(operator => operator.value === "==" || operator.value === "!=");
    }
    if (field.type === "number" || field.type === "integer")
    {
        return CONDITION_OPERATORS.filter(operator => !["contains", "startsWith"].includes(operator.value));
    }
    return CONDITION_OPERATORS.filter(operator => ["==", "!=", "contains", "startsWith"].includes(operator.value));
}

export function parseEnumDescription(description: string | undefined): string[] | null
{
    if (!description) return null;
    const parts = description.split("|").map(value => value.trim());
    return parts.length > 1 && parts.every(value => /^[A-Z][A-Z0-9_]*$/.test(value)) ? parts : null;
}

function unescapeQuotedValue(value: string): string
{
    return value.replace(/\\(['\\])/g, "$1");
}
