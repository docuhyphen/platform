export interface ChoiceOption<T extends string>
{
    value: T;
    label: string;
}

export const optionsFrom = <T extends string>(labels: Record<T, string>): ChoiceOption<T>[] =>
    (Object.keys(labels) as T[]).map(value => ({value, label: labels[value]}));
