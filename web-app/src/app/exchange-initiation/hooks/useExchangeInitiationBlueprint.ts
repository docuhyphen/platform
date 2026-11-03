import {useCallback, useRef, useState} from "react";
import {AvailableVariablesDto, BlueprintConfig, BlueprintDefinitionSummaryDto} from "../../models/models.tsx";
import {getResolvedSchema} from "../../../services/fieldsService.ts";
import {fetchMyOrganizationUsers} from "../../../services/organizationApi.ts";
import useSelectedBlueprint from "./useSelectedBlueprint.ts";
import {participantUsersOf} from "../blueprintParticipants.ts";
import {ExchangeInitiationFormState} from "../exchangeInitiationTypes.ts";
import {
    applyBlueprintConfigToForm,
    isBlueprintLocked,
    parseBlueprintConfig,
} from "../exchangeInitiationBlueprintConfig.ts";
import {collectPendingVariableTokens} from "../blueprintVariableTokens.ts";

const useExchangeInitiationBlueprint = (
    form: ExchangeInitiationFormState,
    availableVariables: AvailableVariablesDto | null,
) =>
{
    const {setInternalParticipants, setSchemaDefinitionId, setFieldValueMap, setFieldBindings} = form;
    const {selectedBlueprint, selectBlueprint, clearBlueprint, startsInformationRequest} = useSelectedBlueprint();
    const [blueprintLocked, setBlueprintLocked] = useState(false);
    const [schemaFromBlueprint, setSchemaFromBlueprint] = useState(false);
    const [variableOverrides, setVariableOverrides] = useState<Record<string, string>>({});
    const [pendingVariableTokens, setPendingVariableTokens] = useState<string[]>([]);
    const appliedBlueprintId = useRef<string | null>(null);

    const clearAppliedBlueprint = useCallback(() =>
    {
        appliedBlueprintId.current = null;
        clearBlueprint();
        setBlueprintLocked(false);
        setInternalParticipants([]);
        setSchemaDefinitionId(undefined);
        setFieldValueMap({});
        setFieldBindings([]);
        setSchemaFromBlueprint(false);
        setVariableOverrides({});
        setPendingVariableTokens([]);
    }, [clearBlueprint, setFieldBindings, setFieldValueMap, setInternalParticipants, setSchemaDefinitionId]);

    const applyBlueprintSchema = (blueprint: BlueprintDefinitionSummaryDto) =>
    {
        if (!blueprint.schemaDefinitionId)
        {
            setSchemaFromBlueprint(false);
            setSchemaDefinitionId(undefined);
            setFieldValueMap({});
            setFieldBindings([]);
            return;
        }
        const schemaId = blueprint.schemaDefinitionId;
        setSchemaFromBlueprint(true);
        setSchemaDefinitionId(schemaId);
        getResolvedSchema(schemaId)
            .then(view =>
            {
                if (appliedBlueprintId.current !== blueprint.id) return;
                setFieldBindings(view.fields);
                const contractByFieldDefinition = new Map(
                    view.fields.map(f => [f.fieldDefinitionId, f.fieldContractId]),
                );
                const seeded: Record<string, unknown> = {};
                (blueprint.fieldDefaults ?? []).forEach(d =>
                {
                    const contractId = contractByFieldDefinition.get(d.fieldDefinitionId);
                    if (contractId && d.value !== undefined && d.value !== null) seeded[contractId] = d.value;
                });
                setFieldValueMap(seeded);
            })
            .catch(() =>
            {
                if (appliedBlueprintId.current !== blueprint.id) return;
                setFieldBindings([]);
                setFieldValueMap({});
            });
    };

    const loadBlueprintParticipants = (blueprint: BlueprintDefinitionSummaryDto) =>
    {
        const participantDefaults = blueprint.participants ?? [];
        if (participantDefaults.length === 0) return;
        void fetchMyOrganizationUsers()
            .then(users =>
            {
                if (appliedBlueprintId.current !== blueprint.id) return;
                setInternalParticipants(participantUsersOf(participantDefaults, users));
            })
            .catch((): undefined => undefined);
    };

    const applyPendingVariableTokens = (blueprint: BlueprintDefinitionSummaryDto, config: BlueprintConfig | null) =>
    {
        if (!availableVariables) return;
        const pending = collectPendingVariableTokens(availableVariables, blueprint, config);
        if (!pending) return;
        setVariableOverrides(pending.defaults);
        setPendingVariableTokens(pending.tokens);
    };

    const applyBlueprint = (blueprint: BlueprintDefinitionSummaryDto) =>
    {
        appliedBlueprintId.current = blueprint.id;
        const config = parseBlueprintConfig(blueprint);
        if (config)
        {
            applyBlueprintConfigToForm(form, blueprint, config);
            loadBlueprintParticipants(blueprint);
            setBlueprintLocked(isBlueprintLocked(blueprint, config));
            applyBlueprintSchema(blueprint);
        }
        selectBlueprint(blueprint);
        applyPendingVariableTokens(blueprint, config);
    };

    const handleSchemaChange = (id: string | undefined) =>
    {
        setSchemaFromBlueprint(false);
        setSchemaDefinitionId(id || undefined);
        setFieldValueMap({});
        setFieldBindings([]);
    };

    return {
        selectedBlueprint,
        startsInformationRequest,
        blueprintLocked,
        schemaFromBlueprint,
        variableOverrides,
        setVariableOverrides,
        pendingVariableTokens,
        clearAppliedBlueprint,
        applyBlueprint,
        handleSchemaChange,
    };
};

export type ExchangeInitiationBlueprintState = ReturnType<typeof useExchangeInitiationBlueprint>;

export default useExchangeInitiationBlueprint;
