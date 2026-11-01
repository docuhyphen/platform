import {BlueprintDefinitionSummaryDto} from '../../models/models.tsx';
import {
    cloneBlueprint,
    deleteBlueprint,
    patchBlueprintPublished,
    patchBlueprintStatus,
} from '../../../services/blueprintService.ts';
import {BlueprintListActions} from './blueprintListTypes.ts';

export const createBlueprintListActions = (
    reload: () => void,
    onEdit: (bp: BlueprintDefinitionSummaryDto) => void,
): BlueprintListActions =>
{
    const thenReload = async (change: Promise<unknown>) =>
    {
        await change;
        reload();
    };

    return {
        onEdit,
        onPublish: bp => thenReload(patchBlueprintPublished(bp.id, {isPublished: !bp.isPublished})),
        onActivate: bp => thenReload(patchBlueprintStatus(bp.id, {isActive: !bp.isActive})),
        onDuplicate: bp => thenReload(cloneBlueprint(bp.id, {newName: `${bp.name} (copy)`})),
        onCloneToPersonal: bp => thenReload(cloneBlueprint(bp.id, {newName: `${bp.name} (copy)`, targetScope: 'PERSONAL'})),
        onCloneToOrg: bp => thenReload(cloneBlueprint(bp.id, {newName: `${bp.name} (copy)`, targetScope: 'ORG'})),
        onDelete: bp => thenReload(deleteBlueprint(bp.id)),
    };
};
