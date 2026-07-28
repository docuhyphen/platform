import {
    BlueprintDefinitionDto,
    BlueprintDefinitionSummaryDto,
} from "../app/models/models.tsx";
import {
    deleteBlueprint,
    listBlueprints,
    patchBlueprintPublished,
    patchBlueprintStatus,
} from "./blueprintService.ts";

const requirePlatformBlueprint = <T extends BlueprintDefinitionSummaryDto>(blueprint: T): T =>
{
    if (blueprint.scope !== "APP")
        throw new Error("Platform Administration can manage only APP-scoped Blueprints.");
    return blueprint;
};

export const listPlatformBlueprints = async (): Promise<BlueprintDefinitionSummaryDto[]> =>
{
    const blueprints = await listBlueprints({scope: "APP"});
    blueprints.forEach(requirePlatformBlueprint);
    return blueprints;
};

export const setPlatformBlueprintPublished = (
    blueprint: BlueprintDefinitionSummaryDto,
): Promise<BlueprintDefinitionDto> =>
{
    requirePlatformBlueprint(blueprint);
    return patchBlueprintPublished(blueprint.id, {isPublished: !blueprint.isPublished});
};

export const setPlatformBlueprintActive = (
    blueprint: BlueprintDefinitionSummaryDto,
): Promise<BlueprintDefinitionDto> =>
{
    requirePlatformBlueprint(blueprint);
    return patchBlueprintStatus(blueprint.id, {isActive: !blueprint.isActive});
};

export const deletePlatformBlueprint = (
    blueprint: BlueprintDefinitionSummaryDto,
): Promise<void> =>
{
    requirePlatformBlueprint(blueprint);
    return deleteBlueprint(blueprint.id);
};
