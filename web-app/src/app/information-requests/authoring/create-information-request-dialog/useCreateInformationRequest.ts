import {useRef, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {
    assignInformationRequestParty,
    createInformationRequest,
    getInformationRequestParties,
} from "../../../../services/informationRequestAuthoringService.ts";
import {getInformationRequestTemplate} from "../../../../services/informationRequestTemplateService.ts";
import {
    CreateInformationRequestRequest,
    InformationRequestDto,
    InformationRequestShareRoleKey,
    InformationRequestTemplateRefusalDto,
} from "../../../models/models.tsx";
import {configurationRequestFrom} from "../../template-document/templateDraftDocument.ts";
import {targetForRefusal} from "../../template-document/templateDraftValidation.ts";
import {useTemplateDocumentEditor} from "../../template-document/useTemplateDocumentEditor.ts";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useRequestSourceOptions} from "./useRequestSourceOptions.ts";

export type RequestSource = "template" | "blueprint" | "adhoc";

const TEMPLATE_REFUSAL = "INFORMATION_REQUEST_TEMPLATE_INVALID";

const isTemplateRefusal = (error: unknown): error is InformationRequestTemplateRefusalDto =>
    (error as InformationRequestTemplateRefusalDto | undefined)?.reasonCode === TEMPLATE_REFUSAL;

export const useCreateInformationRequest = (exchangeId: string, onCreated: (request: InformationRequestDto) => void) =>
{
    const {appUser} = useAuth();
    const options = useRequestSourceOptions();
    const editor = useTemplateDocumentEditor(undefined, "one-off-request", options.schemas, false);
    const [source, setSource] = useState<RequestSource>("template");
    const [templateId, setTemplateId] = useState("");
    const [blueprintId, setBlueprintId] = useState("");
    const [name, setName] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const commandKey = useRef({signature: "", key: ""});

    const ready = source === "template"
        ? Boolean(templateId)
        : source === "blueprint"
            ? Boolean(blueprintId)
            : Boolean(name.trim()) && editor.problems.length === 0;

    const keyFor = (request: CreateInformationRequestRequest): string =>
    {
        const signature = JSON.stringify(request);
        if (commandKey.current.signature !== signature) commandKey.current = {signature, key: crypto.randomUUID()};
        return commandKey.current.key;
    };

    const requestFor = async (): Promise<CreateInformationRequestRequest> =>
    {
        if (source === "blueprint") return {exchangeId, blueprintDefinitionId: blueprintId};
        if (source === "adhoc") return {exchangeId, displayName: name.trim(), configuration: configurationRequestFrom(editor.document)};
        const template = await getInformationRequestTemplate(templateId);
        const versionId = template.latestPublishedVersion?.id;
        if (!versionId) throw {errorMessage: "This Template has no published Version to start from."};
        return {exchangeId, templateVersionId: versionId};
    };

    const nameAuthorDecisionMaker = async (requestId: string) =>
    {
        if (!appUser?.id) return;
        try
        {
            const {partiesETag} = await getInformationRequestParties(requestId);
            await assignInformationRequestParty(
                requestId,
                {roleKey: InformationRequestShareRoleKey.DECISION_MAKER, userId: appUser.id},
                partiesETag,
                crypto.randomUUID(),
            );
        }
        catch
        {
            return;
        }
    };

    const create = async () =>
    {
        if (!ready || busy) return;
        setBusy(true);
        setError(null);
        try
        {
            const request = await requestFor();
            const created = await createInformationRequest(request, keyFor(request));
            await nameAuthorDecisionMaker(created.id);
            onCreated(created);
        }
        catch (caught: unknown)
        {
            setError(informationRequestRefusalMessage(caught, "The Information Request could not be created."));
            if (source === "adhoc" && isTemplateRefusal(caught)) editor.goTo(targetForRefusal(editor.document, caught));
        }
        finally
        {
            setBusy(false);
        }
    };

    return {
        options,
        editor,
        source,
        setSource,
        templateId,
        setTemplateId,
        blueprintId,
        setBlueprintId,
        name,
        setName,
        ready,
        busy,
        error,
        create,
    };
};
