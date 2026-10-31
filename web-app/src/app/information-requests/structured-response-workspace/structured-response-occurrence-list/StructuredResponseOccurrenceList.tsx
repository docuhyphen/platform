import {ReactNode, useMemo} from "react";
import {
    InformationRequestConditionEvaluationDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto
} from "../../../models/models.tsx";
import StructuredResponseOccurrence from "../structured-response-occurrence/StructuredResponseOccurrence.tsx";
import {occurrencePresentation, ResponseEdits} from "../structuredResponseWorkspaceState.ts";
import {OccurrenceCommandResult} from "../StructuredResponseWorkspaceTypes.ts";
import {occurrencesIn, releasesAnOccurrence, takesAnotherOccurrence} from "../occurrenceCardinality.ts";
import {OccurrenceNode, occurrenceTree} from "../occurrenceTree.ts";
import {ResponseAnswerEdits} from "../responseAnswerState.ts";
import {useStructuredResponseOccurrenceListStyles} from "./StructuredResponseOccurrenceListStyles.tsx";

interface Props {
    occurrences: InformationRequestGroupOccurrenceDto[];
    requestId: string;
    responseETag: string;
    busy: boolean;
    readOnly: boolean;
    groups: InformationRequestTemplateGroupDto[];
    creatable: (groupKey: string) => boolean;
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>;
    edits: ResponseEdits;
    setEdits: (edits: (previous: ResponseEdits) => ResponseEdits) => void;
    answers: ResponseAnswerEdits;
    setAnswers: (answers: (previous: ResponseAnswerEdits) => ResponseAnswerEdits) => void;
    onAddOccurrence: (
        requestId: string,
        request: { groupKey: string; parentOccurrenceId?: string | null },
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onRemoveOccurrence: (
        requestId: string,
        occurrenceId: string,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onReorderOccurrences: (
        requestId: string,
        request: { groupKey: string; parentOccurrenceId?: string | null; occurrenceIds: string[] },
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onResult: (result: OccurrenceCommandResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const StructuredResponseOccurrenceList = ({
                                              occurrences,
                                              groups,
                                              creatable,
                                              onAddOccurrence,
                                              onRemoveOccurrence,
                                              onReorderOccurrences,
                                              ...shared
                                          }: Props) => {
    const styles = useStructuredResponseOccurrenceListStyles();
    const tree = useMemo(() => occurrenceTree(occurrences, groups), [groups, occurrences]);

    const renderNode = (node: OccurrenceNode): ReactNode => {
        const presentation = occurrencePresentation(node.occurrence, groups);
        if (!presentation) return null;
        const group = groups.find(candidate => candidate.groupKey === presentation.groupKey);
        const childGroups = presentation.childGroups.filter(child => creatable(child.groupKey));
        const fullChildGroupKeys = childGroups
            .filter(child => !takesAnotherOccurrence(child, occurrencesIn(occurrences, child, node.occurrence.id)))
            .map(child => child.groupKey);
        return (
            <StructuredResponseOccurrence {...shared}
                                          key={node.occurrence.id}
                                          occurrence={node.occurrence}
                                          title={node.label}
                                          presentation={{...presentation, childGroups}}
                                          occurrenceIndex={node.siblingIds.indexOf(node.occurrence.id)}
                                          occurrenceCount={node.siblingIds.length}
                                          siblingOccurrenceIds={node.siblingIds}
                                          removable={releasesAnOccurrence(group, node.siblingIds.length)}
                                          fullChildGroupKeys={fullChildGroupKeys}
                                          nested={node.children.length > 0 ? node.children.map(renderNode) : undefined}
                                          onAdd={(targetRequestId, groupKey, parentOccurrenceId, etag) =>
                                              onAddOccurrence(targetRequestId, {groupKey, parentOccurrenceId}, etag)}
                                          onRemove={onRemoveOccurrence}
                                          onReorder={(targetRequestId, groupKey, parentOccurrenceId, occurrenceIds, etag) =>
                                              onReorderOccurrences(targetRequestId, {
                                                  groupKey,
                                                  parentOccurrenceId,
                                                  occurrenceIds
                                              }, etag)}/>
        );
    };

    return (
        <div id="information-request-response-occurrences"
             className={styles.scrollRegion}>
            {tree.map(renderNode)}
        </div>
    );
};

export default StructuredResponseOccurrenceList;
