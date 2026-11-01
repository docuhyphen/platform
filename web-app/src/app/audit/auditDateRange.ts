export const dateOnlyToRangeStartInstant = (dateOnly: string): string =>
{
    return new Date(`${dateOnly}T00:00:00`).toISOString();
};

export const dateOnlyToRangeEndInstant = (dateOnly: string): string =>
{
    const startOfNextDay = new Date(`${dateOnly}T00:00:00`);
    startOfNextDay.setDate(startOfNextDay.getDate() + 1);
    return startOfNextDay.toISOString();
};
