import React from 'react';
import {mergeClasses} from "@fluentui/react-components";
import BlueprintPicker from "../blueprint-picker/BlueprintPicker.tsx";
import ExchangeInitiationSaveBlueprintPanel
    from "../exchange-initiation-save-blueprint-panel/ExchangeInitiationSaveBlueprintPanel.tsx";
import ExchangeInitiationSuccess from "../exchange-initiation-success/ExchangeInitiationSuccess.tsx";
import ExchangeInitiationTabPanels from "../exchange-initiation-tab-panels/ExchangeInitiationTabPanels.tsx";
import {BlueprintDefinitionSummaryDto} from "../../../models/models.tsx";
import {ExchangeInitiationFormState} from "../../exchangeInitiationTypes.ts";
import {ExchangeInitiationDialogState} from "../../hooks/useExchangeInitiationDialog.ts";
import {ExchangeInitiationBlueprintState} from "../../hooks/useExchangeInitiationBlueprint.ts";
import {ExchangeInitiationSubmitState} from "../../hooks/useExchangeInitiationSubmit.ts";
import {ExchangeInitiationFeatures} from "../../hooks/useExchangeInitiationFeatures.ts";
import {ExchangeInitiationCatalog} from "../../hooks/useExchangeInitiationCatalog.ts";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {useExchangeInitiationDialogContentStyles} from "./ExchangeInitiationDialogContentStyles.tsx";

interface ExchangeInitiationDialogContentProps
{
    form: ExchangeInitiationFormState;
    dialog: ExchangeInitiationDialogState;
    blueprint: ExchangeInitiationBlueprintState;
    submit: ExchangeInitiationSubmitState;
    features: ExchangeInitiationFeatures;
    catalog: ExchangeInitiationCatalog;
    onBlueprintSelect: (blueprint: BlueprintDefinitionSummaryDto) => void;
    onViewExchange: () => void;
    onManageInformationRequest: (requestId: string) => void;
}

const ExchangeInitiationDialogContent: React.FC<ExchangeInitiationDialogContentProps> = (
    {
        form,
        dialog,
        blueprint,
        submit,
        features,
        catalog,
        onBlueprintSelect,
        onViewExchange,
        onManageInformationRequest,
    }) =>
{
    const styles = useExchangeInitiationStyles();
    const contentStyles = useExchangeInitiationDialogContentStyles();

    const dialogContentKey = dialog.saveBlueprintDialogOpen
        ? "save-blueprint"
        : form.exchangeInitiatedSuccessfully
            ? "success"
            : form.choosingBlueprint
                ? "blueprint-picker"
                : "initiation-form";

    const transitionClassName = mergeClasses(
        contentStyles.dialogContentTransitionFrame,
        dialog.contentTransitionDirection === "forward" ? contentStyles.dialogContentSlideInFromRight : undefined,
        dialog.contentTransitionDirection === "back" ? contentStyles.dialogContentSlideInFromLeft : undefined,
    );

    const renderInitiationForm = () => (
        <div id={"exchange-initiation-dialog-content-container"}
             className={styles.dialogContentContainer}>
            {form.choosingBlueprint ? (
                <BlueprintPicker onSelect={onBlueprintSelect}
                                 onCancel={dialog.closeBlueprintPicker}/>
            ) : (
                <ExchangeInitiationTabPanels form={form}
                                             blueprint={blueprint}
                                             features={features}
                                             availableVariables={catalog.availableVariables}
                                             eligibleSchemas={catalog.eligibleSchemas}/>
            )}
        </div>
    );

    return (
        <div id={`exchange-initiation-content-transition-${dialogContentKey}`}
             key={dialogContentKey}
             className={transitionClassName}>
            {dialog.saveBlueprintDialogOpen ? (
                <ExchangeInitiationSaveBlueprintPanel form={form}
                                                      onSaved={dialog.closeSaveBlueprintPanel}/>
            ) : form.exchangeInitiatedSuccessfully ? (
                <ExchangeInitiationSuccess summary={submit.createdExchangeSummary}
                                           fallbackName={form.name}
                                           fallbackDocumentCount={form.documents.length}
                                           fallbackRequiresSignIn={form.requireSignIn}
                                           copyLinkStatus={submit.copyLinkStatus}
                                           onCopyLink={submit.onCopyExchangeLink}
                                           onViewExchange={onViewExchange}
                                           onManageInformationRequest={onManageInformationRequest}/>
            ) : renderInitiationForm()}
        </div>
    );
};

export default ExchangeInitiationDialogContent;
