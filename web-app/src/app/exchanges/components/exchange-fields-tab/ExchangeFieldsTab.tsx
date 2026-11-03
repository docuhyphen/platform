import {useEffect, useState} from 'react';
import {Spinner, Text} from '@fluentui/react-components';
import {
    ExchangeDetailedDto,
    FieldLifecycleStatus,
    SchemaAssignmentStateDto,
    SchemaDefinitionDto,
} from '../../../models/models';
import {getExchangeSchema, listSchemas} from '../../../../services/fieldsService';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import SchemaAssignPanel from './SchemaAssignPanel';
import FieldValuesForm from './FieldValuesForm';
import FieldValuesReadOnly from './FieldValuesReadOnly';
import SchemaSummaryCard from './schema-summary-card/SchemaSummaryCard';

interface Props
{
    exchange: ExchangeDetailedDto;
}

const EXCHANGE_RESOURCE = 'EXCHANGE';

const ExchangeFieldsTab = ({exchange}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();

    const [state, setState] = useState<SchemaAssignmentStateDto | null>(null);
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const assignment = state?.assignment;
    const canManageSchema = state?.canManageSchema ?? false;
    const canEditValues = state?.canEditValues ?? false;
    const bindings = assignment?.bindings ?? [];
    const fieldCount = bindings.length;

    const load = (spinner = true) =>
    {
        if (spinner) setLoading(true);
        setError(null);
        getExchangeSchema(exchange.id)
            .then(async current =>
            {
                setState(current);
                if (!current.assignment && current.canManageSchema)
                {
                    const all = await listSchemas().catch((): SchemaDefinitionDto[] => []);
                    setSchemas(all.filter(s => !!s.latestPublishedVersion
                        && s.status !== FieldLifecycleStatus.RETIRED
                        && s.targetResourceType === EXCHANGE_RESOURCE));
                }
            })
            .catch(() => setError('Failed to load fields'))
            .finally(() =>
            {
                if (spinner) setLoading(false);
            });
    };

    useEffect(() => {
        load();
    }, [exchange.id, exchange.status]);

    if (loading) return <Spinner id="exchange-fields-loading"
                                 size="small"
                                 label="Loading fields..."/>;

    return (
        <div id="exchange-fields-tab-content"
             className={styles.container}>
            {error && (
                <span id="exchange-fields-load-error"
                      className={styles.errorText}>
                    {error}
                </span>
            )}

            {state && !assignment && !canManageSchema && (
                <Text id="exchange-fields-no-schema"
                      className={styles.subText}>
                    No business schema is assigned to this Exchange.
                </Text>
            )}

            {state && !assignment && canManageSchema && (
                <SchemaAssignPanel exchangeId={exchange.id}
                                   schemas={schemas}
                                   onAssigned={load}/>
            )}

            {assignment && fieldCount === 0 && (
                <Text id="exchange-fields-no-visible-fields"
                      className={styles.subText}>
                    No more details have been shared with you for this Exchange.
                </Text>
            )}

            {assignment && fieldCount > 0 && (
                <>
                    <SchemaSummaryCard exchangeId={exchange.id}
                                       assignment={assignment}
                                       fieldCount={fieldCount}
                                       editable={canEditValues}
                                       removable={canManageSchema}
                                       onRemoved={() => load()}
                                       onValuesMovedOn={() => load(false)}/>

                    {canEditValues && assignment.etag
                        ? <FieldValuesForm exchangeId={exchange.id}
                                           bindings={bindings}
                                           values={assignment.fields}
                                           valuesETag={assignment.etag}
                                           onValuesChanged={() => load(false)}/>
                        : <FieldValuesReadOnly bindings={bindings}
                                               values={assignment.fields}/>}
                </>
            )}
        </div>
    );
};

export default ExchangeFieldsTab;
