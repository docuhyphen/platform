import {BlueprintDefinitionSummaryDto} from '../../models/models.tsx';

export type BlueprintListTab = 'PERSONAL' | 'ORG' | 'APP';

export type BlueprintSortOrder = 'default' | 'nameAsc' | 'nameDesc';

export type BlueprintMenuVariant = 'card' | 'table';

export interface BlueprintListActions
{
    onEdit: (bp: BlueprintDefinitionSummaryDto) => void;
    onPublish: (bp: BlueprintDefinitionSummaryDto) => Promise<void>;
    onActivate: (bp: BlueprintDefinitionSummaryDto) => Promise<void>;
    onDuplicate: (bp: BlueprintDefinitionSummaryDto) => Promise<void>;
    onCloneToPersonal: (bp: BlueprintDefinitionSummaryDto) => Promise<void>;
    onCloneToOrg: (bp: BlueprintDefinitionSummaryDto) => Promise<void>;
    onDelete: (bp: BlueprintDefinitionSummaryDto) => Promise<void>;
}

export interface BlueprintListItemContext
{
    activeTab: BlueprintListTab;
    canManageOrganization: boolean;
    canManageItem: (bp: BlueprintDefinitionSummaryDto) => boolean;
    actions: BlueprintListActions;
}
