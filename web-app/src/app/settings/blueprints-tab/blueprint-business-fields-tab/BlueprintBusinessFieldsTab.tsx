import {useEffect, useRef, useState} from 'react';
import {Text} from '@fluentui/react-components';
import {
    BlueprintFieldDefaultConfig,
    SchemaDefinitionDto,
    SchemaFieldBindingDto,
} from '../../../models/models';
import {listSchemas} from '../../../../services/fieldsService';
import {
    buildBlueprintFieldDefaults,
    filterEligibleExchangeSchemas,
} from '../../../exchange-initiation/components/exchange-initiation-fields-tab/creationFieldsUtils';
import ExchangeInitiationFieldsTab
    from '../../../exchange-initiation/components/exchange-initiation-fields-tab/ExchangeInitiationFieldsTab';
import {useBlueprintBusinessFieldsTabStyles} from './BlueprintBusinessFieldsTabStyles';

interface Props
{
    initialSchemaDefinitionId?: string;
    initialFieldDefaults?: BlueprintFieldDefaultConfig[];
    onChange: (schemaDefinitionId: string | undefined, fieldDefaults: BlueprintFieldDefaultConfig[]) => void;
}

/**
 * Blueprint editor tab that classifies a blueprint with a published Exchange schema and captures
 * default field values. Defaults are stored by the stable fieldDefinitionId so they survive schema
 * re-publishing; they are seeded back into the editor by resolving each binding's fieldContractId.
 * Reuses the initiation Fields tab so value controls stay identical.
 */
const BlueprintBusinessFieldsTab = (
    {
        initialSchemaDefinitionId,
        initialFieldDefaults,
        onChange,
    }: Props) =>
{
    const styles = useBlueprintBusinessFieldsTabStyles();
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [schemaDefinitionId, setSchemaDefinitionId] = useState<string | undefined>(initialSchemaDefinitionId);
    const [bindings, setBindings] = useState<SchemaFieldBindingDto[]>([]);
    const [valueMap, setValueMap] = useState<Record<string, unknown>>({});
    const [ready, setReady] = useState(false);
    const seededRef = useRef(false);

    useEffect(() =>
    {
        listSchemas()
            .then(all => setSchemas(filterEligibleExchangeSchemas(all)))
            .catch(() => setSchemas([]));
    }, []);

    useEffect(() =>
    {
        if (!ready) return;
        onChange(schemaDefinitionId, buildBlueprintFieldDefaults(schemaDefinitionId, bindings, valueMap));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ready, schemaDefinitionId, bindings, valueMap]);

    const handleSchemaChange = (id: string | undefined) =>
    {
        seededRef.current = true;
        setSchemaDefinitionId(id);
        setValueMap({});
        setBindings([]);
    };

    const handleBindingsLoaded = (loaded: SchemaFieldBindingDto[]) =>
    {
        setBindings(loaded);
        if (!seededRef.current && initialFieldDefaults && initialFieldDefaults.length > 0)
        {
            const contractByDefinition = new Map(loaded.map(b => [b.fieldDefinitionId, b.fieldContractId]));
            const seeded: Record<string, unknown> = {};
            initialFieldDefaults.forEach(d =>
            {
                const contractId = contractByDefinition.get(d.fieldDefinitionId);
                if (contractId && d.value !== undefined && d.value !== null) seeded[contractId] = d.value;
            });
            setValueMap(seeded);
        }
        seededRef.current = true;
        setReady(true);
    };

    return (
        <div id="blueprint-business-fields-tab"
             className={styles.container}>
            <Text className={styles.hint}>
                Pick a published Exchange schema and set default values. When someone starts an
                Exchange from this blueprint, the schema and defaults are pre-filled.
            </Text>
            <ExchangeInitiationFieldsTab schemas={schemas}
                                         schemaDefinitionId={schemaDefinitionId}
                                         onSchemaChange={handleSchemaChange}
                                         bindings={bindings}
                                         onBindingsLoaded={handleBindingsLoaded}
                                         valueMap={valueMap}
                                         onValueChange={(id, value) => setValueMap(prev => ({...prev, [id]: value}))}/>
        </div>
    );
};

export default BlueprintBusinessFieldsTab;
