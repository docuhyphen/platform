import {useCallback, useState} from "react";
import {AvailableVariablesDto, SchemaDefinitionDto} from "../../models/models.tsx";
import {getAvailableVariables} from "../../../services/variableService.ts";
import {listSchemas} from "../../../services/fieldsService.ts";
import {filterEligibleExchangeSchemas} from "../components/exchange-initiation-fields-tab/creationFieldsUtils.ts";

const useExchangeInitiationCatalog = () =>
{
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);
    const [eligibleSchemas, setEligibleSchemas] = useState<SchemaDefinitionDto[]>([]);

    const loadCatalog = useCallback(() =>
    {
        getAvailableVariables().then(setAvailableVariables).catch((): null => null);
        listSchemas()
            .then(all => setEligibleSchemas(filterEligibleExchangeSchemas(all)))
            .catch(() => setEligibleSchemas([]));
    }, []);

    return {availableVariables, eligibleSchemas, loadCatalog};
};

export type ExchangeInitiationCatalog = ReturnType<typeof useExchangeInitiationCatalog>;

export default useExchangeInitiationCatalog;
