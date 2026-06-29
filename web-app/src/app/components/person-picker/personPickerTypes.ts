export interface PersonPickerItem
{
    id: string;
    email: string;
    firstName?: string | null;
    lastName?: string | null;
    avatarUrl?: string | null;
}

export const getPersonName = (person: PersonPickerItem): string =>
    [person.firstName, person.lastName].filter(Boolean).join(" ").trim() || person.email;

export const matchesPersonQuery = (person: PersonPickerItem, query: string): boolean =>
{
    const normalizedQuery = query.trim().toLocaleLowerCase();
    if (!normalizedQuery) return true;

    const name = getPersonName(person).toLocaleLowerCase();
    const selectedLabel = `${name} (${person.email.toLocaleLowerCase()})`;

    return selectedLabel === normalizedQuery ||
        name.includes(normalizedQuery) ||
        person.email.toLocaleLowerCase().includes(normalizedQuery);
};
