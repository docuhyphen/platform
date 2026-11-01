import React from 'react';
import {TabValue} from "@fluentui/react-components";
import ExchangeInitiationDialogTitleSection
    from "../exchange-initiation-dialog-title-section/ExchangeInitiationDialogTitleSection.tsx";
import ExchangeInitiationErrorMessages from "../exchange-initiation-error-messages/ExchangeInitiationErrorMessages.tsx";
import ExchangeInitiationSaveBlueprintTitle
    from "../exchange-initiation-save-blueprint-title/ExchangeInitiationSaveBlueprintTitle.tsx";
import {ExchangeInitiationFormState} from "../../exchangeInitiationTypes.ts";
import {ExchangeInitiationDialogState} from "../../hooks/useExchangeInitiationDialog.ts";
import {ExchangeInitiationBlueprintState} from "../../hooks/useExchangeInitiationBlueprint.ts";
import {ExchangeInitiationFeatures} from "../../hooks/useExchangeInitiationFeatures.ts";

interface ExchangeInitiationDialogHeaderProps
{
    form: ExchangeInitiationFormState;
    dialog: ExchangeInitiationDialogState;
    blueprint: ExchangeInitiationBlueprintState;
    features: ExchangeInitiationFeatures;
    showFieldsTab: boolean;
}

const ExchangeInitiationDialogHeader: React.FC<ExchangeInitiationDialogHeaderProps> = (
    {
        form,
        dialog,
        blueprint,
        features,
        showFieldsTab,
    }) =>
{
    if (dialog.saveBlueprintDialogOpen)
    {
        return <ExchangeInitiationSaveBlueprintTitle onBack={dialog.closeSaveBlueprintPanel}/>;
    }

    const onTabSelect = (value: TabValue) =>
    {
        form.setMessageGroupMessages([]);
        form.setSelectedTab(String(value));
    };

    const onDismissMessage = (index: number) =>
    {
        form.setMessageGroupMessages(form.messageGroupMessages.filter((_, i) => i !== index));
    };

    return (
        <>
            <ExchangeInitiationDialogTitleSection exchangeInitiatedSuccessfully={form.exchangeInitiatedSuccessfully}
                                                  requestingDocuments={form.requestingDocuments}
                                                  choosingBlueprint={form.choosingBlueprint}
                                                  selectedBlueprintName={blueprint.selectedBlueprint?.name ?? null}
                                                  selectedTab={form.selectedTab}
                                                  showFieldsTab={showFieldsTab}
                                                  onTabSelect={onTabSelect}
                                                  onSaveAsBlueprint={features.canManageBlueprints
                                                      ? dialog.openSaveBlueprintPanel
                                                      : undefined}/>
            <ExchangeInitiationErrorMessages messages={form.messageGroupMessages}
                                             onDismiss={onDismissMessage}/>
        </>
    );
};

export default ExchangeInitiationDialogHeader;
