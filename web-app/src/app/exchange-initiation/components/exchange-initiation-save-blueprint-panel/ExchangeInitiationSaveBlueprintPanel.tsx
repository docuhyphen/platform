import React from 'react';
import SaveBlueprintPanel from "../save-blueprint-dialog/SaveBlueprintDialog.tsx";
import {ExchangeInitiationFormState} from "../../exchangeInitiationTypes.ts";
import {buildBlueprintConfigJson, buildBlueprintDocuments} from "../../exchangeInitiationBlueprintConfig.ts";
import {blueprintParticipantsOf} from "../../blueprintParticipants.ts";
import {buildBlueprintFieldDefaults} from "../exchange-initiation-fields-tab/creationFieldsUtils.ts";

interface ExchangeInitiationSaveBlueprintPanelProps
{
    form: ExchangeInitiationFormState;
    onSaved: () => void;
}

const ExchangeInitiationSaveBlueprintPanel: React.FC<ExchangeInitiationSaveBlueprintPanelProps> = ({form, onSaved}) =>
{
    return (
        <SaveBlueprintPanel onSaved={onSaved}
                            initialName={form.name}
                            configJson={buildBlueprintConfigJson(form)}
                            exchangeDocuments={buildBlueprintDocuments(form.documents)}
                            participants={blueprintParticipantsOf(form.internalParticipants ?? [])}
                            schemaDefinitionId={form.schemaDefinitionId || undefined}
                            fieldDefaults={buildBlueprintFieldDefaults(
                                form.schemaDefinitionId,
                                form.fieldBindings,
                                form.fieldValueMap,
                            )}/>
    );
};

export default ExchangeInitiationSaveBlueprintPanel;
