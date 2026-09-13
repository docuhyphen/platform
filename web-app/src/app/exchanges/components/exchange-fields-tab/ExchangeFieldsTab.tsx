import {useEffect, useState} from 'react';
import {Badge, Spinner, Text} from '@fluentui/react-components';
import {
    ExchangeDetailedDto,
    ExchangeStatus,
    FieldLifecycleStatus,
    SchemaAssignmentDto,
    SchemaDefinitionDto,
} from '../../../models/models';
import {getExchangeSchema, listSchemas} from '../../../../services/fieldsService';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import SchemaAssignPanel from './SchemaAssignPanel';
import FieldValuesForm from './FieldValuesForm';
import FieldValuesReadOnly from './FieldValuesReadOnly';

interface Props
{
    exchange: ExchangeDetailedDto;
}

/** A schema is written for one kind of resource, and only one kind can be given to an Exchange. */
const EXCHANGE_RESOURCE = 'EXCHANGE';

const ExchangeFieldsTab = ({exchange}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();
    const editable = exchange.status === ExchangeStatus.INITIATED;

    const [assignment, setAssignment] = useState<SchemaAssignmentDto | null>(null);
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    /**
     * The questions asked of this reader, and the answers held for them. Both come from the same
     * reading of the Exchange, so a question this reader was not shown is neither described nor
     * answered and the form cannot offer an editor for one it may not save.
     */
    const bindings = assignment?.bindings ?? [];
    const fieldCount = bindings.length;

    /**
     * Reads the assignment, the questions it asks, and the answers it holds. A refresh the form asks
     * for leaves the spinner alone: replacing the form with one would discard entries the responder
     * has not saved yet and the message telling them why their last save was not applied.
     */
    const load = (spinner = true) =>
    {
        if (spinner) setLoading(true);
        setError(null);
        getExchangeSchema(exchange.id)
            .then(async current =>
            {
                setAssignment(current);
                if (!current && editable)
                {
                    const all = await listSchemas().catch(() => []);
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

    useEffect(() => { load(); }, [exchange.id, exchange.status]);

    if (loading) return <Spinner size="small" label="Loading fields..."/>;

    return (
        <div id="exchange-fields-tab-content"
             className={styles.container}>
            {error && <span className={styles.errorText}>{error}</span>}

            {!assignment && !editable && (
                <Text className={styles.subText}>No business schema is assigned to this exchange.</Text>
            )}

            {!assignment && editable && (
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
                    <div id="exchange-fields-schema-summary"
                         className={styles.schemaSummaryCard}>
                        <div className={styles.headerTitleBlock}>
                            <Text className={styles.schemaTitle}>
                                {assignment.displayName}
                            </Text>
                        </div>
                        <div className={styles.schemaBadgeRow}>
                            <Badge appearance="tint"
                                   color="informative"
                                   size="small">
                                v{assignment.versionNumber}
                            </Badge>
                            <Badge appearance="outline"
                                   color="brand"
                                   size="small">
                                {fieldCount} {fieldCount === 1 ? 'field' : 'fields'}
                            </Badge>
                            <Badge appearance="outline"
                                   color={editable ? 'success' : 'warning'}
                                   size="small">
                                {editable ? 'Editable' : 'Read only'}
                            </Badge>
                        </div>
                    </div>

                    {editable
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
