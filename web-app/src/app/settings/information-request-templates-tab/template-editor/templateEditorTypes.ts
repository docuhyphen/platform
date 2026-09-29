import {
    CreateInformationRequestTemplateRequest,
    InformationRequestTemplateConfigurationRequest,
    InformationRequestTemplateDto,
} from "../../../models/models.tsx";

export interface TemplateEditorCommands
{
    save: (configuration: InformationRequestTemplateConfigurationRequest) => Promise<InformationRequestTemplateDto>;
    publish: () => Promise<InformationRequestTemplateDto>;
    startDraft: (sourceVersionNumber: number) => Promise<InformationRequestTemplateDto>;
    retire: (versionNumber: number) => Promise<InformationRequestTemplateDto>;
    clone: (sourceVersionNumber: number, target: CreateInformationRequestTemplateRequest) => Promise<InformationRequestTemplateDto>;
}

export type TemplateEditorPanel = "sections" | "groups" | "conditions" | "review" | "settings" | "versions";
