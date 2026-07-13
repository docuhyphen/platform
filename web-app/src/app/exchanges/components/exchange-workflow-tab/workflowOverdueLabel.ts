const MILLISECONDS_PER_DAY = 1000 * 60 * 60 * 24;

export const formatWorkflowOverdueDuration = (dueAt: Date, now: Date): string =>
{
    const overdueDays = Math.floor((now.getTime() - dueAt.getTime()) / MILLISECONDS_PER_DAY);

    if (overdueDays < 1)
    {
        return "less than 1 day";
    }

    return `${overdueDays} ${overdueDays === 1 ? "day" : "days"}`;
};
