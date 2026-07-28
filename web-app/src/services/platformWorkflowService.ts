import {WorkflowDefinitionSummaryDto} from "../app/models/models.tsx";
import {
    listWorkflowDefinitions,
    patchWorkflowDefinitionPublished,
    patchWorkflowDefinitionStatus,
} from "./workflowService.ts";

const requirePlatformScope = (
    definitions: WorkflowDefinitionSummaryDto[],
): WorkflowDefinitionSummaryDto[] =>
{
    if (definitions.some(definition => definition.scope !== "APP"))
    {
        throw new Error("The platform workflow response contained a tenant-scoped definition.");
    }
    return definitions;
};

export const listPlatformWorkflowTemplates = async (): Promise<WorkflowDefinitionSummaryDto[]> =>
    requirePlatformScope(await listWorkflowDefinitions({scope: "APP"}));

export const setPlatformWorkflowActive = (
    definition: WorkflowDefinitionSummaryDto,
): Promise<unknown> =>
{
    if (definition.scope !== "APP") throw new Error("Cannot update a tenant-scoped workflow.");
    return patchWorkflowDefinitionStatus(definition.id, {isActive: !definition.isActive});
};

export const setPlatformWorkflowPublished = (
    definition: WorkflowDefinitionSummaryDto,
): Promise<unknown> =>
{
    if (definition.scope !== "APP") throw new Error("Cannot update a tenant-scoped workflow.");
    return patchWorkflowDefinitionPublished(definition.id, {isPublished: !definition.isPublished});
};
