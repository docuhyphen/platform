import {WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";

export type WorkflowListTab = 'PERSONAL' | 'ORG' | 'APP';

export interface WorkflowListItemActions
{
    onEdit: (definition: WorkflowDefinitionSummaryDto) => void;
    onToggleActive: (definition: WorkflowDefinitionSummaryDto) => void;
    onTogglePublished: (definition: WorkflowDefinitionSummaryDto) => void;
    onClone: (definition: WorkflowDefinitionSummaryDto) => void;
    onDelete: (definition: WorkflowDefinitionSummaryDto) => void;
}
