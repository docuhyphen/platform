import {useState} from "react";
import {CreateInformationRequestTemplateRequest, InformationRequestTemplateRefusalDto} from "../../../models/models.tsx";
import {configurationRequestFrom} from "../../../information-requests/template-document/templateDraftDocument.ts";
import {TemplateDraftTarget} from "../../../information-requests/template-document/templateDraftRules.ts";
import {targetForRefusal} from "../../../information-requests/template-document/templateDraftValidation.ts";
import {TemplateDocumentEditor} from "../../../information-requests/template-document/useTemplateDocumentEditor.ts";
import {TemplateEditorCommands} from "./templateEditorTypes.ts";

export interface TemplateRefusal
{
    message: string;
    target?: TemplateDraftTarget;
}

const isTemplateRefusal = (value: unknown): value is InformationRequestTemplateRefusalDto =>
    typeof value === "object" && value !== null && "errorMessage" in value;

export const useTemplateEditorLifecycle = (editor: TemplateDocumentEditor, commands: TemplateEditorCommands) =>
{
    const [busy, setBusy] = useState(false);
    const [refusal, setRefusal] = useState<TemplateRefusal | null>(null);

    const run = async (command: () => Promise<unknown>, failure: string): Promise<boolean> =>
    {
        setBusy(true);
        setRefusal(null);
        try
        {
            await command();
            return true;
        }
        catch (caught: unknown)
        {
            if (isTemplateRefusal(caught))
            {
                setRefusal({message: caught.errorMessage || failure, target: targetForRefusal(editor.document, caught)});
            }
            else setRefusal({message: typeof caught === "string" && caught ? caught : failure});
            return false;
        }
        finally
        {
            setBusy(false);
        }
    };

    const save = async (): Promise<boolean> =>
    {
        const saved = await run(() => commands.save(configurationRequestFrom(editor.document)), "The draft could not be saved.");
        if (saved) editor.markSaved();
        return saved;
    };

    const publish = async () =>
    {
        if (editor.dirty && !await save()) return;
        await run(commands.publish, "The draft could not be published.");
    };

    return {
        busy,
        refusal,
        dismissRefusal: () => setRefusal(null),
        save,
        publish,
        startDraft: (versionNumber: number) =>
            run(() => commands.startDraft(versionNumber), "A new draft could not be started."),
        retire: (versionNumber: number) =>
            run(() => commands.retire(versionNumber), "The version could not be retired."),
        clone: (versionNumber: number, target: CreateInformationRequestTemplateRequest) =>
            run(() => commands.clone(versionNumber, target), "The Template could not be copied."),
    };
};
