import React from 'react';
import ExchangeInitiationRecipientsTab
    from "../exchange-initiation-recipients-tab/ExchangeInitiationRecipientsTab.tsx";
import ExchangeInitiationDetailsTab from "../exchange-initiation-details-tab/ExchangeInitiationDetailsTab.tsx";
import ExchangeInitiationFieldsTab from "../exchange-initiation-fields-tab/ExchangeInitiationFieldsTab.tsx";
import ExchangeInitiationDocumentsPanel
    from "../exchange-initiation-documents-panel/ExchangeInitiationDocumentsPanel.tsx";
import ExchangeInitiationOptionsPanel from "../exchange-initiation-options-panel/ExchangeInitiationOptionsPanel.tsx";
import ExchangeInitiationVariableOverridesPanel
    from "../exchange-initiation-variable-overrides-panel/ExchangeInitiationVariableOverridesPanel.tsx";
import {handleInputChange} from "../../formHandlers.tsx";
import {AvailableVariablesDto, SchemaDefinitionDto} from "../../../models/models.tsx";
import {ExchangeInitiationFormState} from "../../exchangeInitiationTypes.ts";
import {ExchangeInitiationBlueprintState} from "../../hooks/useExchangeInitiationBlueprint.ts";
import {ExchangeInitiationFeatures} from "../../hooks/useExchangeInitiationFeatures.ts";
import {useExchangeInitiationTabPanelsStyles} from "./ExchangeInitiationTabPanelsStyles.tsx";

interface ExchangeInitiationTabPanelsProps
{
    form: ExchangeInitiationFormState;
    blueprint: ExchangeInitiationBlueprintState;
    features: ExchangeInitiationFeatures;
    availableVariables: AvailableVariablesDto | null;
    eligibleSchemas: SchemaDefinitionDto[];
}

const ExchangeInitiationTabPanels: React.FC<ExchangeInitiationTabPanelsProps> = (
    {
        form,
        blueprint,
        features,
        availableVariables,
        eligibleSchemas,
    }) =>
{
    const styles = useExchangeInitiationTabPanelsStyles();
    const locked = blueprint.blueprintLocked;

    return (
        <div id={"exchange-initiation-taps"}
             className={styles.exchangeInitiationTaps}>
            {blueprint.pendingVariableTokens.length > 0 && (
                <ExchangeInitiationVariableOverridesPanel tokens={blueprint.pendingVariableTokens}
                                                          overrides={blueprint.variableOverrides}
                                                          onOverrideChange={(token, value) =>
                                                              blueprint.setVariableOverrides(prev =>
                                                                  ({...prev, [token]: value}))}/>
            )}
            {form.selectedTab === "recipients-tab" && (
                <ExchangeInitiationRecipientsTab recipientMode={form.recipientMode}
                                                 setRecipientMode={form.setRecipientMode}
                                                 recipientOrg={form.recipientOrg}
                                                 setRecipientOrg={form.setRecipientOrg}
                                                 recipientOrgUser={form.recipientOrgUser}
                                                 setRecipientOrgUser={form.setRecipientOrgUser}
                                                 recipientOrgGroup={form.recipientOrgGroup}
                                                 setRecipientOrgGroup={form.setRecipientOrgGroup}
                                                 setRecipientResolution={form.setRecipientResolution}
                                                 internalParticipants={form.internalParticipants}
                                                 setInternalParticipants={form.setInternalParticipants}
                                                 newRecipient={form.newRecipient}
                                                 setNewRecipient={form.setNewRecipient}
                                                 isRequestingDocuments={form.requestingDocuments}
                                                 recipientRole={form.recipientRole}
                                                 setRecipientRole={form.setRecipientRole}
                                                 recipientConstraints={form.recipientConstraints}
                                                 setRecipientConstraints={form.setRecipientConstraints}
                                                 allowAdditionalParticipants={features.allowAdditionalParticipants}
                                                 allowAdvancedAccessControls={features.allowAdvancedAccessControls}/>
            )}
            {form.selectedTab === "details-tab" && (
                <ExchangeInitiationDetailsTab name={form.name}
                                              description={form.description}
                                              initialShareMessage={form.initialShareMessage}
                                              onExchangeNameChange={handleInputChange(form.setExchangeName)}
                                              onDescriptionChange={handleInputChange(form.setDescription)}
                                              onInitialShareMessageChange={handleInputChange(form.setInitialShareMessage)}
                                              setMessageGroupMessages={form.setMessageGroupMessages}
                                              availableVariables={availableVariables ?? undefined}
                                              onNameChange={form.setExchangeName}
                                              onDescChange={form.setDescription}
                                              onMessageChange={form.setInitialShareMessage}
                                              locked={locked}/>
            )}
            {form.selectedTab === "fields-tab" && (
                <ExchangeInitiationFieldsTab schemas={eligibleSchemas}
                                             schemaDefinitionId={form.schemaDefinitionId}
                                             onSchemaChange={blueprint.handleSchemaChange}
                                             bindings={form.fieldBindings}
                                             onBindingsLoaded={form.setFieldBindings}
                                             valueMap={form.fieldValueMap}
                                             onValueChange={(fieldContractId, value) =>
                                                 form.setFieldValueMap(prev => ({...prev, [fieldContractId]: value}))}
                                             locked={locked}/>
            )}
            {form.selectedTab === "documents-tab" && (
                <ExchangeInitiationDocumentsPanel form={form}
                                                  availableVariables={availableVariables}
                                                  locked={locked}
                                                  canUseDocumentLibrary={features.canUseDocumentLibrary}
                                                  startsInformationRequest={blueprint.startsInformationRequest}/>
            )}
            {form.selectedTab === "options-tab" && (
                <ExchangeInitiationOptionsPanel form={form}
                                                locked={locked}/>
            )}
        </div>
    );
};

export default ExchangeInitiationTabPanels;
