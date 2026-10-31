import {useState} from "react";
import {
    CreateInformationRequestTemplateRequest,
    InformationRequestTemplateRefusalDto
} from "../../../models/models.tsx";
import {configurationRequestFrom} from "../../../information-requests/template-document/templateDraftDocument.ts";
import {TemplateDraftTarget} from "../../../information-requests/template-document/templateDraftRules.ts";
import {targetForRefusal} from "../../../information-requests/template-document/templateDraftValidation.ts";
import {TemplateDocumentEditor} from "../../../information-requests/template-document/useTemplateDocumentEditor.ts";
import {isStaleInformationRequestCommand} from "../../../information-requests/shared/informationRequestRefusal.ts";
import {TemplateEditorCommands} from "./templateEditorTypes.ts";

const STALE_DRAFT = "This draft was changed after you opened it, so your change was not made. " +
    "Select Open latest draft to continue from it.";

const STALE_CHANGES = "This draft was changed after you opened it, so your changes were not saved. Select Keep my changes " +
    "to save them over the latest draft, or Open latest draft to discard them.";

const DRAFT_GONE = "This draft was published or replaced after you opened it, so there is no draft to save your changes to. " +
    "Select Open latest draft to continue from the latest version.";

const LATEST_FAILURE = "The latest draft could not be loaded.";

export interface TemplateRefusal
{
    message: string;
    target?: TemplateDraftTarget;
    stale?: boolean;
    keepable?: boolean;
}

const TEMPLATE_INVALID = "INFORMATION_REQUEST_TEMPLATE_INVALID";

const isTemplateRefusal = (value: unknown): value is InformationRequestTemplateRefusalDto =>
    typeof value === "object" && value !== null && "errorMessage" in value;

export const useTemplateEditorLifecycle = (editor: TemplateDocumentEditor, commands: TemplateEditorCommands) =>
{
    const [busy, setBusy] = useState(false);
    const [refusal, setRefusal] = useState<TemplateRefusal | null>(null);

    const refusalOf = (caught: unknown, failure: string): TemplateRefusal => {
        if (isStaleInformationRequestCommand(caught)) {
            return {message: editor.dirty ? STALE_CHANGES : STALE_DRAFT, stale: true, keepable: editor.dirty};
        }
        if (isTemplateRefusal(caught)) {
            const target = caught.reasonCode === TEMPLATE_INVALID ? targetForRefusal(editor.document, caught) : undefined;
            return {message: caught.errorMessage || failure, target};
        }
        return {message: typeof caught === "string" && caught ? caught : failure};
    };

    const attempt = async (command: () => Promise<unknown>, failure: string): Promise<TemplateRefusal | null> =>
    {
        setBusy(true);
        try
        {
            await command();
            return null;
        }
        catch (caught: unknown)
        {
            return refusalOf(caught, failure);
        }
        finally
        {
            setBusy(false);
        }
    };

    const run = async (command: () => Promise<unknown>, failure: string): Promise<boolean> => {
        setRefusal(null);
        const refused = await attempt(command, failure);
        setRefusal(refused);
        return refused === null;
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

    const keepChanges = async () => {
        setRefusal(null);
        let adopted = false;
        const refused = await attempt(async () => {
            adopted = await commands.adoptLatestDraft();
        }, LATEST_FAILURE);
        if (refused) setRefusal(refused);
        else if (!adopted) setRefusal({message: DRAFT_GONE, stale: true});
        else await save();
    };

    return {
        busy,
        refusal,
        dismissRefusal: () => setRefusal(null),
        save,
        publish,
        keepChanges,
        openLatest: () => run(commands.reload, LATEST_FAILURE),
        startDraft: (versionNumber: number) =>
            run(() => commands.startDraft(versionNumber), "A new draft could not be started."),
        retire: async (versionNumber: number): Promise<string | null> =>
            (await attempt(() => commands.retire(versionNumber), "The version could not be retired."))?.message ?? null,
        clone: async (versionNumber: number, target: CreateInformationRequestTemplateRequest): Promise<string | null> =>
            (await attempt(() => commands.clone(versionNumber, target), "The Template could not be copied."))?.message ?? null,
    };
};
