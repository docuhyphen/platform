import {Badge} from '@fluentui/react-components';
import {SchemaDefinitionDto, SchemaTargetResourceType} from '../../../../models/models';
import {useSchemaStatusBadgesStyles} from './SchemaStatusBadgesStyles';

interface Props
{
    schema: SchemaDefinitionDto;
}

const SchemaStatusBadges = ({schema}: Props) =>
{
    const styles = useSchemaStatusBadgesStyles();

    return (
        <div id={"schema-card-div-1"}
             className={styles.badgeRow}>
            {schema.targetResourceType === SchemaTargetResourceType.INFORMATION_REQUEST && (
                <Badge id={`schema-target-${schema.id}`}
                       appearance="outline"
                       color="brand"
                       size="small">
                    Information Requests
                </Badge>
            )}
            {schema.latestPublishedVersion && (
                <Badge id={"schema-card-badge-1"}
                       appearance="tint"
                       color="success"
                       size="small">
                    Published v{schema.latestPublishedVersion.versionNumber}
                </Badge>
            )}
            {schema.draftVersion && (
                <Badge id={"schema-card-badge-2"}
                       appearance="tint"
                       color="warning"
                       size="small">
                    Draft v{schema.draftVersion.versionNumber}
                </Badge>
            )}
        </div>
    );
};

export default SchemaStatusBadges;
