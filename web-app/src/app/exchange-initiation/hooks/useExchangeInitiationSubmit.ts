import {useCallback, useState} from "react";
import {AppUserDetailedDto, CurrentSessionDto} from "../../models/models.tsx";
import {initiateExchange} from "../../../services/exchangeApi.ts";
import {publishNewExchangeAddition} from "../../observable/exchangeObservables.ts";
import {
    CreatedExchangeSummary,
    ExchangeInitiationCopyLinkStatus,
    ExchangeInitiationFormState,
} from "../exchangeInitiationTypes.ts";
import {exchangeInitiationDocumentProblem} from "../exchangeInitiationDocuments.ts";
import {exchangeInitiationRefusalMessage} from "../exchangeInitiationRefusal.ts";
import {findStartedInformationRequest} from "../startedInformationRequest.ts";
import {recipientValidationProblem} from "../exchangeInitiationRecipientValidation.ts";
import {buildExchangeInitiationRequest} from "../exchangeInitiationRequestBuilder.ts";
import {buildRecipientLabel} from "../exchangeInitiationRecipientLabel.ts";
import {buildExchangeShareLink, copyTextWithFallback} from "../exchangeInitiationShareLink.ts";

interface ExchangeInitiationSubmitOptions
{
    form: ExchangeInitiationFormState;
    appUser: AppUserDetailedDto | null;
    refreshCurrentSession: () => Promise<CurrentSessionDto | null>;
    startsInformationRequest: boolean;
    schemaFromBlueprint: boolean;
    variableOverrides: Record<string, string>;
    blueprintDefinitionId?: string;
    onServerError: (message: string) => void;
}

const useExchangeInitiationSubmit = (options: ExchangeInitiationSubmitOptions) =>
{
    const {
        form,
        appUser,
        refreshCurrentSession,
        startsInformationRequest,
        schemaFromBlueprint,
        variableOverrides,
        blueprintDefinitionId,
        onServerError,
    } = options;
    const [createdExchangeSummary, setCreatedExchangeSummary] = useState<CreatedExchangeSummary | null>(null);
    const [copyLinkStatus, setCopyLinkStatus] = useState<ExchangeInitiationCopyLinkStatus>('idle');

    const rejectWith = (message: string, tab: string): boolean =>
    {
        form.setMessageGroupMessages([message]);
        form.setSelectedTab(tab);
        return false;
    };

    const isSubmissionValid = (): boolean =>
    {
        const recipientProblem = recipientValidationProblem(form, appUser);
        if (recipientProblem) return rejectWith(recipientProblem, 'recipients-tab');
        if (!form.name) return rejectWith('Exchange name is required', 'details-tab');
        const documentProblem = exchangeInitiationDocumentProblem(form.documents, startsInformationRequest);
        if (documentProblem) return rejectWith(documentProblem, 'documents-tab');
        return true;
    };

    const onInitiateExchange = async () =>
    {
        if (form.initiatingExchange)
        {
            return;
        }

        form.setMessageGroupMessages([]);
        form.setInitiatingExchange(true);

        try
        {
            if (!isSubmissionValid())
            {
                return;
            }

            const request = buildExchangeInitiationRequest(form, {
                appUser,
                variableOverrides,
                schemaFromBlueprint,
                blueprintDefinitionId,
            });
            const createdExchange = await initiateExchange(request);
            const createdExchangeId = (createdExchange as { id?: string })?.id;
            const shareLink = createdExchangeId
                ? buildExchangeShareLink(createdExchangeId, form.requireSignIn)
                : undefined;
            const informationRequest = startsInformationRequest && createdExchangeId
                ? await findStartedInformationRequest(createdExchangeId)
                : undefined;

            setCreatedExchangeSummary({
                id: createdExchangeId,
                name: form.name.trim(),
                recipient: buildRecipientLabel(form),
                documents: form.documents.length,
                requiresSignIn: form.requireSignIn,
                shareLink,
                informationRequest,
            });
            setCopyLinkStatus('idle');

            publishNewExchangeAddition(createdExchange);

            form.setExchangeInitiatedSuccessfully(true);
            await refreshCurrentSession();
        }
        catch (error: unknown)
        {
            onServerError(exchangeInitiationRefusalMessage(error));
        }
        finally
        {
            form.setInitiatingExchange(false);
        }
    };

    const onCopyExchangeLink = async () =>
    {
        const link = createdExchangeSummary?.shareLink;
        if (!link)
        {
            setCopyLinkStatus('failed');
            return;
        }

        const copied = await copyTextWithFallback(link);
        setCopyLinkStatus(copied ? 'copied' : 'failed');
    };

    const clearCreatedSummary = useCallback(() =>
    {
        setCreatedExchangeSummary(null);
        setCopyLinkStatus('idle');
    }, []);

    return {createdExchangeSummary, copyLinkStatus, onInitiateExchange, onCopyExchangeLink, clearCreatedSummary};
};

export type ExchangeInitiationSubmitState = ReturnType<typeof useExchangeInitiationSubmit>;

export default useExchangeInitiationSubmit;
