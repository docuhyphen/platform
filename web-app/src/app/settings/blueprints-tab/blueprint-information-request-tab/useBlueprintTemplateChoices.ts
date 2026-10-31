import {listInformationRequestTemplates} from "../../../../services/informationRequestTemplateService.ts";
import {
    BlueprintScope,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
} from "../../../models/models.tsx";
import {useServerRead} from "../../../information-requests/shared/useServerRead.ts";

export interface BlueprintTemplateChoice
{
    templateId: string;
    displayName: string;
    versionId: string;
    versionNumber: number;
}

const CHOICES_FAILURE = "The Templates this Blueprint can use could not be loaded.";

const NO_CHOICES: BlueprintTemplateChoice[] = [];

const ownerScopeFor = (scope: BlueprintScope): InformationRequestTemplateScopeKind | null =>
{
    if (scope === "ORG") return InformationRequestTemplateScopeKind.ORGANIZATION;
    if (scope === "PERSONAL") return InformationRequestTemplateScopeKind.PERSONAL;
    return null;
};

const choicesFrom = (templates: InformationRequestTemplateSummaryDto[]): BlueprintTemplateChoice[] =>
    templates.flatMap(template =>
        template.status !== InformationRequestTemplateStatus.RETIRED
        && template.latestPublishedVersionId
        && template.latestPublishedVersionNumber
            ? [{
                templateId: template.id,
                displayName: template.displayName,
                versionId: template.latestPublishedVersionId,
                versionNumber: template.latestPublishedVersionNumber,
            }]
            : []);

export const useBlueprintTemplateChoices = (scope: BlueprintScope) =>
{
    const ownerScope = ownerScopeFor(scope);
    const read = useServerRead(
        ownerScope ?? "none",
        () => ownerScope === null
            ? Promise.resolve(NO_CHOICES)
            : listInformationRequestTemplates({scopeKind: ownerScope}).then(choicesFrom),
        () => CHOICES_FAILURE,
    );

    return {
        choices: read.value ?? NO_CHOICES,
        loaded: !read.loading,
        error: read.error,
        reload: read.reload,
        available: ownerScope !== null,
    };
};
