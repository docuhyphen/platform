import {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {listBlueprints} from "../../../../services/blueprintService.ts";
import {listSchemas} from "../../../../services/fieldsService.ts";
import {listInformationRequestTemplates} from "../../../../services/informationRequestTemplateService.ts";
import {
    BlueprintDefinitionSummaryDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
    SchemaDefinitionDto,
} from "../../../models/models.tsx";
import {distinctSchemas, schemaScopesFor} from "../../../settings/information-request-templates-tab/informationRequestTemplateScope.ts";

export interface RequestSourceOptions
{
    templates: InformationRequestTemplateSummaryDto[];
    blueprints: BlueprintDefinitionSummaryDto[];
    schemas: SchemaDefinitionDto[];
    loaded: boolean;
}

const publishedTemplates = (lists: InformationRequestTemplateSummaryDto[][]): InformationRequestTemplateSummaryDto[] =>
    lists.flat().filter(template =>
        template.latestPublishedVersionNumber !== undefined &&
        template.latestPublishedVersionNumber !== null &&
        template.status !== InformationRequestTemplateStatus.RETIRED);

const requestBlueprints = (lists: BlueprintDefinitionSummaryDto[][]): BlueprintDefinitionSummaryDto[] =>
    lists.flat().filter(blueprint => blueprint.isActive && Boolean(blueprint.informationRequestTemplateVersionId));

export const useRequestSourceOptions = (): RequestSourceOptions =>
{
    const {currentSession} = useAuth();
    const ownerScope = currentSession?.activeOrganizationId
        ? InformationRequestTemplateScopeKind.ORGANIZATION
        : InformationRequestTemplateScopeKind.PERSONAL;
    const [options, setOptions] = useState<RequestSourceOptions>({templates: [], blueprints: [], schemas: [], loaded: false});

    useEffect(() =>
    {
        let active = true;
        const templateScopes = [ownerScope];
        const blueprintScopes = [ownerScope === InformationRequestTemplateScopeKind.ORGANIZATION ? "ORG" : "PERSONAL", "APP"];
        Promise.all([
            Promise.all(templateScopes.map(scopeKind => listInformationRequestTemplates({scopeKind}).catch(() => []))),
            Promise.all(blueprintScopes.map(scope => listBlueprints({scope}).catch(() => []))),
            Promise.all(schemaScopesFor(ownerScope).map(scopeKind => listSchemas({scopeKind}).catch(() => []))),
        ]).then(([templateLists, blueprintLists, schemaLists]) =>
        {
            if (!active) return;
            setOptions({
                templates: publishedTemplates(templateLists),
                blueprints: requestBlueprints(blueprintLists),
                schemas: distinctSchemas(schemaLists),
                loaded: true,
            });
        });
        return () =>
        {
            active = false;
        };
    }, [ownerScope]);

    return options;
};
