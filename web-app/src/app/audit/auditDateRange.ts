/**
 * Converts a date-only value from an `<input type="date">` control (a plain "YYYY-MM-DD" string,
 * no timezone) into the inclusive range-start instant the backend audit search expects. The
 * boundary is anchored to the browser's local timezone, matching how the user reads the picker.
 */
export const dateOnlyToRangeStartInstant = (dateOnly: string): string =>
{
    return new Date(`${dateOnly}T00:00:00`).toISOString();
};

/**
 * Converts a date-only value into the exclusive range-end instant the backend audit search
 * expects. Using the start of the next local day includes every database timestamp on the
 * selected day without relying on JavaScript's millisecond precision.
 */
export const dateOnlyToRangeEndInstant = (dateOnly: string): string =>
{
    const startOfNextDay = new Date(`${dateOnly}T00:00:00`);
    startOfNextDay.setDate(startOfNextDay.getDate() + 1);
    return startOfNextDay.toISOString();
};
