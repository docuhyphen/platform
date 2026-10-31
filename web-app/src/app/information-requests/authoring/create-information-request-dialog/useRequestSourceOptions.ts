import {listBlueprints} from "../../../../services/blueprintService.ts";
import {listSchemas} from "../../../../services/fieldsService.ts";
import {listInformationRequestTemplates} from "../../../../services/informationRequestTemplateService.ts";
import {
    BlueprintDefinitionSummaryDto,
    InformationRequestOwnerType,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
    SchemaDefinitionDto,
} from "../../../models/models.tsx";
import {fieldScopeFor} from "../../../settings/information-request-templates-tab/informationRequestTemplateScope.ts";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {InformationRequestOwnerFields, useOwnerWorkspace, workspaceScopeOf} from "../../shared/ownerWorkspace.ts";
import {useServerRead} from "../../shared/useServerRead.ts";

interface ListedSources
{
    templates: InformationRequestTemplateSummaryDto[];
    blueprints: BlueprintDefinitionSummaryDto[];
    schemas: SchemaDefinitionDto[];
    blueprintsUnavailable: boolean;
}

export interface RequestSourceOptions extends ListedSources {
    scope: string;
    loaded: boolean;
    error: string | null;
    otherWorkspace: boolean;
    reload: () => Promise<void>;
}

const LOAD_FAILURE = "The Templates and Blueprints to start from could not be loaded.";

const NO_SOURCES: ListedSources = {templates: [], blueprints: [], schemas: [], blueprintsUnavailable: false};

const publishedTemplates = (lists: InformationRequestTemplateSummaryDto[][]): InformationRequestTemplateSummaryDto[] =>
    lists.flat().filter(template =>
        template.latestPublishedVersionNumber !== undefined &&
        template.latestPublishedVersionNumber !== null &&
        template.status !== InformationRequestTemplateStatus.RETIRED);

const requestBlueprints = (lists: BlueprintDefinitionSummaryDto[][]): BlueprintDefinitionSummaryDto[] =>
    lists.flat().filter(blueprint => blueprint.isActive && Boolean(blueprint.informationRequestTemplateVersionId));

const listSources = async (ownerScope: InformationRequestTemplateScopeKind): Promise<ListedSources> =>
{
    const blueprintScopes = [ownerScope === InformationRequestTemplateScopeKind.ORGANIZATION ? "ORG" : "PERSONAL", "APP"];
    const [templateLists, blueprintLists, schemas] = await Promise.all([
        Promise.all([ownerScope].map(scopeKind => listInformationRequestTemplates({scopeKind}))),
        Promise.all(blueprintScopes.map(scope => listBlueprints({scope}))).catch((): null => null),
        listSchemas({scopeKind: fieldScopeFor(ownerScope)}),
    ]);
    return {
        templates: publishedTemplates(templateLists),
        blueprints: blueprintLists ? requestBlueprints(blueprintLists) : [],
        schemas,
        blueprintsUnavailable: blueprintLists === null,
    };
};

const templateScopeOf = (owner: InformationRequestOwnerFields): InformationRequestTemplateScopeKind =>
    owner.ownerType === InformationRequestOwnerType.ORGANIZATION
        ? InformationRequestTemplateScopeKind.ORGANIZATION
        : InformationRequestTemplateScopeKind.PERSONAL;

export const useRequestSourceOptions = (owner: InformationRequestOwnerFields | null): RequestSourceOptions => {
    const workspace = useOwnerWorkspace(owner);
    const scope = workspaceScopeOf(workspace);
    const current = workspace.kind === "current" && owner !== null;
    const read = useServerRead(
        scope,
        () => current && owner ? listSources(templateScopeOf(owner)) : Promise.resolve(NO_SOURCES),
        refusal => informationRequestRefusalMessage(refusal, LOAD_FAILURE),
    );
    const listed = current ? read.value : null;

    return {
        templates: listed?.templates ?? [],
        blueprints: listed?.blueprints ?? [],
        schemas: listed?.schemas ?? [],
        blueprintsUnavailable: listed?.blueprintsUnavailable ?? false,
        scope,
        loaded: current ? read.value !== null : workspace.kind === "other",
        error: current ? read.error : null,
        otherWorkspace: workspace.kind === "other",
        reload: read.reload,
    };
};
