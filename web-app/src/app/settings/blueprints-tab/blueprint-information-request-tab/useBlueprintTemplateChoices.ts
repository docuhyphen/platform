import {useEffect, useState} from "react";
import {getInformationRequestTemplate, listInformationRequestTemplates} from "../../../../services/informationRequestTemplateService.ts";
import {BlueprintScope, InformationRequestTemplateScopeKind} from "../../../models/models.tsx";

export interface BlueprintTemplateChoice
{
    templateId: string;
    displayName: string;
    versionId: string;
    versionNumber: number;
}

const scopesFor = (scope: BlueprintScope): InformationRequestTemplateScopeKind[] =>
{
    if (scope === "APP") return [InformationRequestTemplateScopeKind.PLATFORM];
    const owner = scope === "ORG" ? InformationRequestTemplateScopeKind.ORGANIZATION : InformationRequestTemplateScopeKind.PERSONAL;
    return [owner, InformationRequestTemplateScopeKind.PLATFORM];
};

export const useBlueprintTemplateChoices = (scope: BlueprintScope) =>
{
    const [choices, setChoices] = useState<BlueprintTemplateChoice[]>([]);
    const [loaded, setLoaded] = useState(false);

    useEffect(() =>
    {
        let active = true;
        Promise.all(scopesFor(scope).map(scopeKind => listInformationRequestTemplates({scopeKind}).catch(() => [])))
            .then(lists => lists.flat().filter(template => template.latestPublishedVersionNumber !== undefined))
            .then(templates => Promise.all(templates.map(template => getInformationRequestTemplate(template.id).catch(() => null))))
            .then(details =>
            {
                if (!active) return;
                setChoices(details.flatMap(detail =>
                    detail?.latestPublishedVersion
                        ? [{
                            templateId: detail.id,
                            displayName: detail.displayName,
                            versionId: detail.latestPublishedVersion.id,
                            versionNumber: detail.latestPublishedVersion.versionNumber,
                        }]
                        : []));
                setLoaded(true);
            });
        return () =>
        {
            active = false;
        };
    }, [scope]);

    return {choices, loaded};
};
