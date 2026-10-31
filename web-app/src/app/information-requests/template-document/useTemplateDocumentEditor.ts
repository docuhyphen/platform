import {useCallback, useMemo, useState} from "react";
import {FieldLifecycleStatus, InformationRequestTemplateVersionDto, SchemaDefinitionDto} from "../../models/models.tsx";
import {TemplateDocumentState} from "./TemplateDocumentContext.ts";
import {draftDocumentFromVersion, TemplateDraftDocument} from "./templateDraftDocument.ts";
import {TemplateDraftTarget, TemplateSchemaFacts} from "./templateDraftRules.ts";
import {templateDraftProblems} from "./templateDraftValidation.ts";
import {useNamedSchemaVersion} from "./useNamedSchemaVersion.ts";

export type TemplateDocumentPanel = "sections" | "groups" | "conditions" | "review" | "settings" | "extra";

export interface TemplateDocumentEditor extends TemplateDocumentState
{
    dirty: boolean;
    panel: TemplateDocumentPanel;
    setPanel: (panel: TemplateDocumentPanel) => void;
    goTo: (target: TemplateDraftTarget) => void;
    markSaved: () => void;
    reloadFieldBindings: () => void;
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

    const named = useNamedSchemaVersion(schemas, current.document.schemaVersionId);
    const fieldBindings = named.bindings;
    const collectableFieldIds = useMemo(
        () => named.known ? new Set(fieldBindings.map(binding => binding.fieldDefinitionId)) : undefined,
        [fieldBindings, named.known],
    );
    const readOnlyFieldIds = useMemo(
        () => new Set(fieldBindings.filter(binding => binding.isReadOnly).map(binding => binding.fieldDefinitionId)),
        [fieldBindings],
    );
    const schemaVersionId = current.document.schemaVersionId;
    const facts = useMemo<TemplateSchemaFacts>(() => ({
        retired: schemas.some(schema => schema.status === FieldLifecycleStatus.RETIRED
            && schema.latestPublishedVersion?.id === schemaVersionId),
        optionCodes: new Map(fieldBindings
            .filter(binding => binding.options.length > 0)
            .map(binding => [binding.fieldDefinitionId, new Set(binding.options.map(option => option.code))])),
    }), [fieldBindings, schemaVersionId, schemas]);
    const problems = useMemo(
        () => templateDraftProblems(current.document, collectableFieldIds, readOnlyFieldIds, facts),
        [collectableFieldIds, current.document, facts, readOnlyFieldIds],
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
        fieldBindingsError: named.error,
        problems,
        focus,
        update,
        clearFocus: () => setFocus(null),
        panel,
        setPanel,
        goTo,
        markSaved: () => setTracked(previous => ({...previous, dirty: false})),
        reloadFieldBindings: named.reload,
    };
};
