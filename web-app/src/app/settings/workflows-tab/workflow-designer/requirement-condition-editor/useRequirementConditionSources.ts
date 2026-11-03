import {useEffect, useMemo, useState} from "react";
import {listSchemas} from "../../../../../services/fieldsService.ts";
import {
    getInformationRequestTemplate,
    listInformationRequestTemplates
} from "../../../../../services/informationRequestTemplateService.ts";
import {
    FieldScopeKind,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
    InformationRequestTemplateVersionDto,
    SchemaDefinitionDto,
} from "../../../../models/models.tsx";
import {useServerRead} from "../../../../information-requests/shared/useServerRead.ts";
import {requirementChoicesOf} from "./requirementConditionChoices.ts";

const REQUEST_SCHEMA_TARGET = "INFORMATION_REQUEST";

const TEMPLATES_FAILURE = "The organization Templates could not be loaded.";

const ANSWERS_FAILURE = "The typed answers of this Template could not be loaded.";

const NO_TEMPLATES: InformationRequestTemplateSummaryDto[] = [];

const published = (template: InformationRequestTemplateSummaryDto): boolean =>
    template.latestPublishedVersionNumber !== undefined &&
    template.latestPublishedVersionNumber !== null &&
    template.status !== InformationRequestTemplateStatus.RETIRED;

const holdsAny = (version: InformationRequestTemplateVersionDto | null | undefined, requirementIds: Set<string>): boolean =>
    version?.sections.some(section =>
        section.requirements.some(requirement => requirementIds.has(requirement.templateRequirementId))) ?? false;

export const useRequirementConditionSources = (storedRequirementIds: string[]) => {
    const templateRead = useServerRead(
        "organization-templates",
        () => listInformationRequestTemplates({scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION})
            .then(templates => templates.filter(published)),
        () => TEMPLATES_FAILURE,
    );
    const templates = templateRead.value ?? NO_TEMPLATES;
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [picked, setPicked] = useState<string | null>(null);
    const [holding, setHolding] = useState("");
    const templateId = picked ?? holding;
    const storedKey = storedRequirementIds.join(" ");
    const versionRead = useServerRead<InformationRequestTemplateVersionDto | undefined>(
        `template:${templateId}`,
        () => templateId
            ? getInformationRequestTemplate(templateId).then(template => template.latestPublishedVersion ?? undefined)
            : Promise.resolve(undefined),
        () => ANSWERS_FAILURE,
    );

    useEffect(() => {
        let active = true;
        Promise.all([FieldScopeKind.ORGANIZATION, FieldScopeKind.PLATFORM]
            .map(scopeKind => listSchemas({scopeKind}).catch((): SchemaDefinitionDto[] => [])))
            .then(schemaLists => {
                if (active) setSchemas(schemaLists.flat().filter(schema => schema.targetResourceType === REQUEST_SCHEMA_TARGET));
            });
        return () => {
            active = false;
        };
    }, []);

    useEffect(() => {
        if (picked !== null || storedKey === "" || templates.length === 0) return;
        let active = true;
        const wanted = new Set(storedKey.split(" "));
        const find = async () => {
            for (const template of templates) {
                const detail = await getInformationRequestTemplate(template.id).catch((): null => null);
                if (!active) return;
                if (holdsAny(detail?.latestPublishedVersion, wanted)) {
                    setHolding(template.id);
                    return;
                }
            }
        };
        void find();
        return () => {
            active = false;
        };
    }, [picked, storedKey, templates]);

    const version = versionRead.value ?? undefined;
    const choices = useMemo(() => requirementChoicesOf(version, schemas), [version, schemas]);

    return {
        templates,
        templatesError: templateRead.error,
        reloadTemplates: templateRead.reload,
        templateId,
        setTemplateId: setPicked,
        choices,
        loading: templateId !== "" && versionRead.loading,
        answersError: templateId ? versionRead.error : null,
        reloadAnswers: versionRead.reload,
    };
};
