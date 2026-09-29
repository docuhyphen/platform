import {
    InformationRequestLineageDto,
    InformationRequestLineageKind,
    InformationRequestRecurrenceDto,
    InformationRequestRecurrenceUnit,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const UNIT_WORDS: Record<InformationRequestRecurrenceUnit, [string, string]> = {
    [InformationRequestRecurrenceUnit.DAY]: ["day", "days"],
    [InformationRequestRecurrenceUnit.WEEK]: ["week", "weeks"],
    [InformationRequestRecurrenceUnit.MONTH]: ["month", "months"],
    [InformationRequestRecurrenceUnit.YEAR]: ["year", "years"],
};

export const recurrenceUnitLabels: Record<InformationRequestRecurrenceUnit, string> = {
    [InformationRequestRecurrenceUnit.DAY]: "Days",
    [InformationRequestRecurrenceUnit.WEEK]: "Weeks",
    [InformationRequestRecurrenceUnit.MONTH]: "Months",
    [InformationRequestRecurrenceUnit.YEAR]: "Years",
};

export const recurrenceSentence = (recurrence: InformationRequestRecurrenceDto): string =>
{
    const [one, many] = UNIT_WORDS[recurrence.intervalUnit];
    const every = recurrence.intervalCount === 1 ? `1 ${one}` : `${recurrence.intervalCount} ${many}`;
    const limit = recurrence.maximumOccurrences ? `, ${recurrence.maximumOccurrences} times in all` : "";
    return `Repeats every ${every} from ${formatInformationRequestTime(recurrence.firstDueAt)}${limit}.`;
};

export const lineageLabel = (lineage: InformationRequestLineageDto): string =>
{
    switch (lineage.lineageKind)
    {
        case InformationRequestLineageKind.RECURRENCE:
            return `Recurring request ${lineage.recurrenceSequence ?? ""}`.trim();
        case InformationRequestLineageKind.SUPPLEMENT:
            return "Supplement";
        case InformationRequestLineageKind.REFRESH:
            return "Refreshed request";
        case InformationRequestLineageKind.SUPERSEDING:
            return "Replacement request";
    }
};
