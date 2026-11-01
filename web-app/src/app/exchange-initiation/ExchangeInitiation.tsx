import React from 'react';
import {
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Toaster,
    useId,
    useToastController,
} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import useExchangeInitiatingState from './hooks/useExchangeInitiatingState.ts';
import useExchangeInitiationFeatures from "./hooks/useExchangeInitiationFeatures.ts";
import useExchangeInitiationCatalog from "./hooks/useExchangeInitiationCatalog.ts";
import useExchangeInitiationBlueprint from "./hooks/useExchangeInitiationBlueprint.ts";
import useExchangeInitiationSubmit from "./hooks/useExchangeInitiationSubmit.ts";
import useExchangeInitiationDialog from "./hooks/useExchangeInitiationDialog.ts";
import ExchangeInitiationDialogActions
    from "./components/exchange-initiation-dialog-actions/ExchangeInitiationDialogActions.tsx";
import ExchangeInitiationDialogTrigger
    from "./components/exchange-initiation-dialog-trigger/ExchangeInitiationDialogTrigger.tsx";
import ExchangeInitiationDialogHeader
    from "./components/exchange-initiation-dialog-header/ExchangeInitiationDialogHeader.tsx";
import ExchangeInitiationDialogContent
    from "./components/exchange-initiation-dialog-content/ExchangeInitiationDialogContent.tsx";
import ExchangeInitiationErrorToast from "./components/exchange-initiation-error-toast/ExchangeInitiationErrorToast.tsx";
import {useExchangeInitiationStyles} from "./ExchangeInitiationStyles.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {BlueprintDefinitionSummaryDto} from "../models/models.tsx";
import {applyRequestingDocumentsMode} from "./exchangeInitiationFormDefaults.ts";
import {informationRequestManagePath} from "../information-requests/shared/informationRequestWorkspacePaths.ts";

const ExchangeInitiation: React.FC = () =>
{
    const styles = useExchangeInitiationStyles();
    const {appUser, refreshCurrentSession} = useAuth();
    const navigate = useNavigate();
    const form = useExchangeInitiatingState();
    const features = useExchangeInitiationFeatures(form);
    const catalog = useExchangeInitiationCatalog();
    const blueprint = useExchangeInitiationBlueprint(form, catalog.availableVariables);
    const toasterId = useId("exchange-initiation-toaster");
    const {dispatchToast} = useToastController(toasterId);

    const showServerErrorToast = (message: string) =>
        dispatchToast(<ExchangeInitiationErrorToast message={message}/>, {intent: 'error', timeout: 15000});

    const submit = useExchangeInitiationSubmit({
        form,
        appUser,
        refreshCurrentSession,
        startsInformationRequest: blueprint.startsInformationRequest,
        schemaFromBlueprint: blueprint.schemaFromBlueprint,
        variableOverrides: blueprint.variableOverrides,
        blueprintDefinitionId: blueprint.selectedBlueprint?.id,
        onServerError: showServerErrorToast,
    });

    const dialog = useExchangeInitiationDialog({
        form,
        canUseBlueprints: features.canUseBlueprints,
        clearAppliedBlueprint: blueprint.clearAppliedBlueprint,
        clearCreatedSummary: submit.clearCreatedSummary,
        loadCatalog: catalog.loadCatalog,
    });

    const leaveDialogFor = (path: string) =>
    {
        dialog.resetInitiationForm();
        dialog.setIsDialogOpen(false);
        navigate(path);
    };

    const onViewExchange = () =>
    {
        const exchangeId = submit.createdExchangeSummary?.id;
        if (!exchangeId) return;
        leaveDialogFor(`/exchanges?tab=inbox&role=outgoing&s=${encodeURIComponent(exchangeId)}`);
    };

    const onManageInformationRequest = (requestId: string) => leaveDialogFor(informationRequestManagePath(requestId));

    const onBlueprintSelect = (selected: BlueprintDefinitionSummaryDto) =>
    {
        blueprint.applyBlueprint(selected);
        dialog.completeBlueprintChoice();
    };

    return (
        <Dialog modalType="alert"
                open={dialog.isDialogOpen}
                onOpenChange={dialog.onDialogOpenChange}>
            <DialogTrigger disableButtonEnhancement>
                <ExchangeInitiationDialogTrigger onRequestingDocumentsChange={(isRequesting) =>
                                                     applyRequestingDocumentsMode(form, isRequesting)}
                                                 onChooseBlueprint={dialog.openBlueprintPicker}
                                                 canUseBlueprints={features.canUseBlueprints}/>
            </DialogTrigger>
            <DialogSurface id={"exchange-initiation-dialog"}
                           className={styles.dialog}>
                <DialogBody id={"exchange-initiation-dialog-actions-dialog-body"}>
                    <DialogTitle id={"exchange-initiation-dialog-title"}
                                 className={styles.dialogTitle}>
                        <ExchangeInitiationDialogHeader form={form}
                                                        dialog={dialog}
                                                        blueprint={blueprint}
                                                        features={features}
                                                        showFieldsTab={features.canUseBusinessFields
                                                            && catalog.eligibleSchemas.length > 0}/>
                    </DialogTitle>
                    <DialogContent id={"exchange-initiation-dialog-content"}
                                   className={styles.dialogContent}>
                        <ExchangeInitiationDialogContent form={form}
                                                         dialog={dialog}
                                                         blueprint={blueprint}
                                                         submit={submit}
                                                         features={features}
                                                         catalog={catalog}
                                                         onBlueprintSelect={onBlueprintSelect}
                                                         onViewExchange={onViewExchange}
                                                         onManageInformationRequest={onManageInformationRequest}/>
                    </DialogContent>
                    {!dialog.saveBlueprintDialogOpen && (
                        <DialogActions id={"exchange-initiation-dialog-actions"}>
                            <ExchangeInitiationDialogActions requestingDocuments={form.requestingDocuments}
                                                             initiatingExchange={form.initiatingExchange}
                                                             exchangeInitiatedSuccessfully={form.exchangeInitiatedSuccessfully}
                                                             choosingBlueprint={form.choosingBlueprint}
                                                             onResetInitiation={dialog.resetInitiationForm}
                                                             onCloseDialog={dialog.closeDialog}
                                                             onInitiateExchange={() => submit.onInitiateExchange()}/>
                        </DialogActions>
                    )}
                    <Toaster id={"exchange-initiation-dialog-toaster"}
                             inline
                             toasterId={toasterId}
                             position="bottom"/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeInitiation;
