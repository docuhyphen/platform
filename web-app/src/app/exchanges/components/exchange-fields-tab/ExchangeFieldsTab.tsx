import {useEffect, useMemo, useState} from 'react';
import {Badge, Spinner, Text} from '@fluentui/react-components';
import {
    ExchangeDetailedDto,
    ExchangeStatus,
    FieldLifecycleStatus,
    ResolvedSchemaViewDto,
    SchemaAssignmentDto,
    SchemaDefinitionDto,
} from '../../../models/models';
import {getExchangeSchema, getResolvedSchema, listSchemas} from '../../../../services/fieldsService';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import SchemaAssignPanel from './SchemaAssignPanel';
import FieldValuesForm from './FieldValuesForm';
import FieldValuesReadOnly from './FieldValuesReadOnly';
import {groupBindingsBySection} from './fieldLayoutUtils';

interface Props
{
    exchange: ExchangeDetailedDto;
}

const ExchangeFieldsTab = ({exchange}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();
    const editable = exchange.status === ExchangeStatus.INITIATED;

    const [assignment, setAssignment] = useState<SchemaAssignmentDto | null>(null);
    const [resolved, setResolved] = useState<ResolvedSchemaViewDto | null>(null);
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const fieldCount = resolved?.fields.length ?? assignment?.fields.length ?? 0;
    const sectionCount = useMemo(() =>
    {
        if (resolved?.fields?.length)
        {
            return groupBindingsBySection(resolved.fields).length;
        }

        if (assignment?.fields?.length)
        {
            return 1;
        }

        return 0;
    }, [assignment?.fields, resolved?.fields]);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        getExchangeSchema(exchange.id)
            .then(async current =>
            {
                setAssignment(current);
                if (current)
                {
                    const view = await getResolvedSchema(current.schemaDefinitionId).catch(() => null);
                    setResolved(view);
                }
                else
                {
                    setResolved(null);
                    if (editable)
                    {
                        const all = await listSchemas().catch(() => []);
                        setSchemas(all.filter(s => !!s.latestPublishedVersion
                            && s.status !== FieldLifecycleStatus.RETIRED));
                    }
                }
            })
            .catch(() => setError('Failed to load fields'))
            .finally(() => setLoading(false));
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

            {assignment && (
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

                    {editable && resolved
                        ? <FieldValuesForm exchangeId={exchange.id}
                                           bindings={resolved.fields}
                                           values={assignment.fields}
                                           onSaved={load}/>
                        : <FieldValuesReadOnly bindings={resolved?.fields ?? []}
                                               values={assignment.fields}/>}
                </>
            )}
        </div>
    );
};

export default ExchangeFieldsTab;
