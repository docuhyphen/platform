import {InformationRequestGroupOccurrenceDto} from "../../models/models.tsx";
import {OccurrenceCommandResult} from "./StructuredResponseWorkspaceTypes.ts";

interface CommandHandlers
{
    occurrence: InformationRequestGroupOccurrenceDto;
    requestId: string;
    responseETag: string;
    groupKey: string;
    onReorder: (
        requestId: string,
        groupKey: string,
        parentOccurrenceId: string | undefined,
        occurrenceIds: string[],
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onResult: (result: OccurrenceCommandResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

export const useStructuredResponseOccurrenceCommands = ({
    occurrence,
    requestId,
    responseETag,
    groupKey,
    onReorder,
    onResult,
    onCommandStart,
    onCommandFailure,
}: CommandHandlers) =>
{
    const runCommand = (command: () => Promise<OccurrenceCommandResult>) =>
    {
        onCommandStart();
        command()
            .then(onResult)
            .catch(onCommandFailure);
    };
    const reorder = (orderedIds: string[]) => runCommand(() =>
        onReorder(requestId, groupKey, occurrence.parentOccurrenceId, orderedIds, responseETag));
    return {runCommand, reorder};
};
