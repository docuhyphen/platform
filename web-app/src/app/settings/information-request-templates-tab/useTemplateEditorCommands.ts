import {RefObject, useMemo} from "react";
import {CreateInformationRequestTemplateRequest, InformationRequestTemplateDto} from "../../models/models.tsx";
import {
    cloneInformationRequestTemplate,
    createInformationRequestTemplateDraftVersion,
    getInformationRequestTemplate,
    publishInformationRequestTemplateDraft,
    replaceInformationRequestTemplateDraftConfiguration,
    retireInformationRequestTemplateVersion,
} from "../../../services/informationRequestTemplateService.ts";
import {StableKeyedCommand} from "../../information-requests/shared/useStableIdempotencyKey.ts";
import {TemplateEditorCommands} from "./template-editor/templateEditorTypes.ts";

export const useTemplateEditorCommands = (
    templateId: string | undefined,
    draftETag: RefObject<string | undefined>,
    keyed: StableKeyedCommand,
    opened: (template: InformationRequestTemplateDto) => InformationRequestTemplateDto,
): TemplateEditorCommands | null =>
    useMemo(() => {
        if (!templateId) return null;
        const id = templateId;
        return {
            save: async configuration =>
                opened(await replaceInformationRequestTemplateDraftConfiguration(id, configuration, draftETag.current ?? "")),
            publish: async () => {
                const expected = draftETag.current ?? "";
                return opened(await keyed(`publish:${id}`, expected, key =>
                    publishInformationRequestTemplateDraft(id, expected, key)));
            },
            startDraft: async versionNumber =>
                opened(await keyed(`start-draft:${id}`, versionNumber, key =>
                    createInformationRequestTemplateDraftVersion(id, {sourceVersionNumber: versionNumber}, key))),
            retire: async versionNumber =>
                opened(await keyed(`retire:${id}`, versionNumber, key =>
                    retireInformationRequestTemplateVersion(id, versionNumber, key))),
            clone: async (versionNumber, target: CreateInformationRequestTemplateRequest) =>
                opened(await keyed(`clone:${id}`, {versionNumber, target}, key =>
                    cloneInformationRequestTemplate(id, {sourceVersionNumber: versionNumber, target}, key))),
            adoptLatestDraft: async () => {
                const latest = await getInformationRequestTemplate(id);
                if (!latest.draftVersion || !latest.draftETag) return false;
                draftETag.current = latest.draftETag;
                return true;
            },
            reload: async () => opened(await getInformationRequestTemplate(id)),
        };
    }, [templateId, draftETag, keyed, opened]);
