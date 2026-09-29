import {useCallback, useMemo, useState} from "react";
import {InformationRequestTemplateVersionDto, SchemaDefinitionDto} from "../../models/models.tsx";
import {bindingsForVersion, TemplateDocumentState} from "./TemplateDocumentContext.ts";
import {draftDocumentFromVersion, TemplateDraftDocument} from "./templateDraftDocument.ts";
import {TemplateDraftTarget} from "./templateDraftRules.ts";
import {templateDraftProblems} from "./templateDraftValidation.ts";

export type TemplateDocumentPanel = "sections" | "groups" | "conditions" | "review" | "settings" | "extra";

export interface TemplateDocumentEditor extends TemplateDocumentState
{
    dirty: boolean;
    panel: TemplateDocumentPanel;
    setPanel: (panel: TemplateDocumentPanel) => void;
    goTo: (target: TemplateDraftTarget) => void;
    markSaved: () => void;
}

interface Tracked
{
    sourceKey: string;
    document: TemplateDraftDocument;
    dirty: boolean;
}

export const useTemplateDocumentEditor = (
    source: InformationRequestTemplateVersionDto | undefined,
    sourceKey: string,
    schemas: SchemaDefinitionDto[],
    readOnly: boolean,
): TemplateDocumentEditor =>
{
    const [tracked, setTracked] = useState<Tracked>(() => ({
        sourceKey,
        document: draftDocumentFromVersion(source),
        dirty: false,
    }));
    const [panel, setPanel] = useState<TemplateDocumentPanel>("sections");
    const [focus, setFocus] = useState<TemplateDraftTarget | null>(null);
    const current = tracked.sourceKey === sourceKey
        ? tracked
        : {sourceKey, document: draftDocumentFromVersion(source), dirty: false};
    if (current !== tracked) setTracked(current);

    const fieldBindings = useMemo(
        () => bindingsForVersion(schemas, current.document.schemaVersionId),
        [schemas, current.document.schemaVersionId],
    );
    const collectableFieldIds = useMemo(() =>
    {
        const knownVersion = schemas.some(schema => schema.latestPublishedVersion?.id === current.document.schemaVersionId);
        return knownVersion ? new Set(fieldBindings.map(binding => binding.fieldDefinitionId)) : undefined;
    }, [current.document.schemaVersionId, fieldBindings, schemas]);
    const problems = useMemo(
        () => templateDraftProblems(current.document, collectableFieldIds),
        [collectableFieldIds, current.document],
    );

    const update = useCallback((change: (document: TemplateDraftDocument) => TemplateDraftDocument) =>
    {
        if (readOnly) return;
        setTracked(previous => ({...previous, document: change(previous.document), dirty: true}));
    }, [readOnly]);

    const goTo = useCallback((target: TemplateDraftTarget) =>
    {
        setPanel(target.panel);
        setFocus(target);
    }, []);

    return {
        document: current.document,
        dirty: current.dirty,
        readOnly,
        schemas,
        fieldBindings,
        problems,
        focus,
        update,
        clearFocus: () => setFocus(null),
        panel,
        setPanel,
        goTo,
        markSaved: () => setTracked(previous => ({...previous, dirty: false})),
    };
};
