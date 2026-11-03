import {InformationRequestGroupOccurrenceDto, InformationRequestTemplateGroupDto} from "../../models/models.tsx";
import {groupLabel, occurrenceLabel} from "./responseAnswerState.ts";
import {ROOT_OCCURRENCE_PATH} from "./structuredResponseWorkspaceState.ts";

export interface OccurrenceNode {
    occurrence: InformationRequestGroupOccurrenceDto;
    label: string;
    siblingIds: string[];
    children: OccurrenceNode[];
}

const bySiblingOrder = (occurrences: InformationRequestGroupOccurrenceDto[]): InformationRequestGroupOccurrenceDto[] =>
    occurrences
        .map((occurrence, arrival) => ({occurrence, arrival}))
        .sort((first, second) => first.occurrence.occurrenceIndex - second.occurrence.occurrenceIndex || first.arrival - second.arrival)
        .map(({occurrence}) => occurrence);

export const occurrenceTree = (
    occurrences: InformationRequestGroupOccurrenceDto[],
    groups: InformationRequestTemplateGroupDto[],
): OccurrenceNode[] => {
    const placed = new Set<string>();
    const nodesIn = (group: InformationRequestTemplateGroupDto, parentId: string | null, parentLabel: string | null): OccurrenceNode[] =>
        bySiblingOrder(occurrences.filter(occurrence =>
            occurrence.sourceTemplateGroupId === group.id && (occurrence.parentOccurrenceId ?? null) === parentId))
            .map((occurrence, index, siblings) => {
                placed.add(occurrence.id);
                const name = `${parentLabel === null ? groupLabel(group.groupKey) : groupLabel(group.groupKey).toLowerCase()} ${index + 1}`;
                const label = parentLabel === null ? name : `${parentLabel}, ${name}`;
                return {
                    occurrence,
                    label,
                    siblingIds: siblings.map(sibling => sibling.id),
                    children: groups
                        .filter(child => child.parentGroupKey === group.groupKey)
                        .flatMap(child => nodesIn(child, occurrence.id, label)),
                };
            });
    const root = occurrences
        .filter(occurrence => occurrence.occurrencePath === ROOT_OCCURRENCE_PATH)
        .map((occurrence): OccurrenceNode => {
            placed.add(occurrence.id);
            return {occurrence, label: "", siblingIds: [occurrence.id], children: []};
        });
    const entries = groups.filter(group => !group.parentGroupKey).flatMap(group => nodesIn(group, null, null));
    const unplaced = occurrences
        .filter(occurrence => !placed.has(occurrence.id))
        .map((occurrence): OccurrenceNode => ({
            occurrence,
            label: occurrenceLabel(occurrence.occurrencePath),
            siblingIds: [occurrence.id],
            children: []
        }));
    return [...root, ...entries, ...unplaced];
};

export const occurrenceLabels = (nodes: OccurrenceNode[]): Map<string, string> =>
    new Map(nodes.flatMap(node => [[node.occurrence.occurrencePath, node.label] as const, ...occurrenceLabels(node.children)]));
