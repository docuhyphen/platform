export interface CorrectedValue
{
    value?: unknown;
    problem?: string;
}

const YES = ["true", "yes"];
const NO = ["false", "no"];

export const correctedValueOf = (original: unknown, text: string): CorrectedValue =>
{
    const trimmed = text.trim();
    if (!trimmed) return {};
    if (typeof original === "number")
    {
        const number = Number(trimmed);
        return Number.isFinite(number) ? {value: number} : {problem: "Enter a number."};
    }
    if (typeof original === "boolean")
    {
        if (YES.includes(trimmed.toLowerCase())) return {value: true};
        if (NO.includes(trimmed.toLowerCase())) return {value: false};
        return {problem: "Enter yes or no."};
    }
    if (Array.isArray(original)) return {value: trimmed.split(",").map(part => part.trim()).filter(Boolean)};
    return {value: trimmed};
};
