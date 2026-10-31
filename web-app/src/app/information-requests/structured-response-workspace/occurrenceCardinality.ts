import {InformationRequestGroupOccurrenceDto, InformationRequestTemplateGroupDto} from "../../models/models.tsx";

export const takesAnotherOccurrence = (group: InformationRequestTemplateGroupDto, count: number): boolean =>
    group.maxOccurrences === undefined || group.maxOccurrences === null || count < group.maxOccurrences;

export const releasesAnOccurrence = (group: InformationRequestTemplateGroupDto | undefined, count: number): boolean =>
    group === undefined || count > group.minOccurrences;

export const occurrencesIn = (
    occurrences: InformationRequestGroupOccurrenceDto[],
    group: InformationRequestTemplateGroupDto,
    parentOccurrenceId: string | null,
): number =>
    occurrences.filter(occurrence =>
        occurrence.sourceTemplateGroupId === group.id && (occurrence.parentOccurrenceId ?? null) === parentOccurrenceId).length;
