import {SchemaTargetResourceType} from '../../models/models';

export const schemaTargetOf = (targetResourceType: string): SchemaTargetResourceType =>
    targetResourceType === SchemaTargetResourceType.INFORMATION_REQUEST
        ? SchemaTargetResourceType.INFORMATION_REQUEST
        : SchemaTargetResourceType.EXCHANGE;
