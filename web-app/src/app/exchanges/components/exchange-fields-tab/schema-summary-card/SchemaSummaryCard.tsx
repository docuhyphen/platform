import {Badge, Text} from '@fluentui/react-components';
import {SchemaAssignmentDto} from '../../../../models/models';
import SchemaRemoveAction from '../schema-remove-action/SchemaRemoveAction';
import {useSchemaSummaryCardStyles} from './SchemaSummaryCardStyles';

interface Props {
    exchangeId: string;
    assignment: SchemaAssignmentDto;
    fieldCount: number;
    editable: boolean;
    removable: boolean;
    onRemoved: () => void;
    onValuesMovedOn: () => void;
}

const SchemaSummaryCard = ({
                               exchangeId,
                               assignment,
                               fieldCount,
                               editable,
                               removable,
                               onRemoved,
                               onValuesMovedOn,
                           }: Props) => {
    const styles = useSchemaSummaryCardStyles();

    return (
        <div id="exchange-fields-schema-summary"
             className={styles.card}>
            <div id="exchange-fields-schema-title-block"
                 className={styles.titleBlock}>
                <Text id="exchange-fields-schema-title"
                      className={styles.title}>
                    {assignment.displayName}
                </Text>
            </div>
            <div id="exchange-fields-schema-badges"
                 className={styles.badgeRow}>
                <Badge id="exchange-fields-schema-version"
                       appearance="tint"
                       color="informative"
                       size="small">
                    v{assignment.versionNumber}
                </Badge>
                <Badge id="exchange-fields-schema-field-count"
                       appearance="outline"
                       color="brand"
                       size="small">
                    {fieldCount} {fieldCount === 1 ? 'field' : 'fields'}
                </Badge>
                <Badge id="exchange-fields-schema-editability"
                       appearance="outline"
                       color={editable ? 'success' : 'warning'}
                       size="small">
                    {editable ? 'Editable' : 'Read only'}
                </Badge>
                {removable && assignment.etag && (
                    <SchemaRemoveAction exchangeId={exchangeId}
                                        valuesETag={assignment.etag}
                                        onRemoved={onRemoved}
                                        onValuesMovedOn={onValuesMovedOn}/>
                )}
            </div>
        </div>
    );
};

export default SchemaSummaryCard;
