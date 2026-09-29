import {createContext, useContext} from "react";
import {SchemaDefinitionDto, SchemaFieldBindingDto} from "../../models/models.tsx";
import {TemplateDraftDocument} from "./templateDraftDocument.ts";
import {TemplateDraftProblem, TemplateDraftTarget} from "./templateDraftRules.ts";

export interface TemplateDocumentState
{
    document: TemplateDraftDocument;
    readOnly: boolean;
    schemas: SchemaDefinitionDto[];
    fieldBindings: SchemaFieldBindingDto[];
    problems: TemplateDraftProblem[];
    focus: TemplateDraftTarget | null;
    update: (change: (document: TemplateDraftDocument) => TemplateDraftDocument) => void;
    clearFocus: () => void;
}

export const TemplateDocumentContext = createContext<TemplateDocumentState | null>(null);

export const useTemplateDocument = (): TemplateDocumentState =>
{
    const state = useContext(TemplateDocumentContext);
    if (!state) throw new Error("A Template document editor part was rendered outside its editor");
    return state;
};

export const requestSchemasOf = (schemas: SchemaDefinitionDto[]): SchemaDefinitionDto[] =>
    schemas.filter(schema => schema.targetResourceType === "INFORMATION_REQUEST" && Boolean(schema.latestPublishedVersion));

export const bindingsForVersion = (schemas: SchemaDefinitionDto[], schemaVersionId?: string): SchemaFieldBindingDto[] =>
    requestSchemasOf(schemas).find(schema => schema.latestPublishedVersion?.id === schemaVersionId)
        ?.latestPublishedVersion?.bindings ?? [];

export const problemsAt = (problems: TemplateDraftProblem[], sectionIndex: number, requirementIndex?: number): TemplateDraftProblem[] =>
    problems.filter(problem => problem.target.panel === "sections"
        && problem.target.sectionIndex === sectionIndex
        && problem.target.requirementIndex === requirementIndex);
