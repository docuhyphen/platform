import {CommunicationSummaryDto} from '../../models/models';
import {
    cloneCommunication,
    deleteCommunication,
    patchCommunicationPublished,
    patchCommunicationStatus,
} from '../../../services/communicationService';
import {CommunicationListActions} from './communicationListTypes';

export const createCommunicationListActions = (
    reload: () => void,
    openEdit: (communication: CommunicationSummaryDto) => void,
): CommunicationListActions => ({
    onEdit: openEdit,
    onPublish: async (communication) =>
    {
        await patchCommunicationPublished(communication.id, {isPublished: !communication.isPublished});
        reload();
    },
    onActivate: async (communication) =>
    {
        await patchCommunicationStatus(communication.id, {isActive: !communication.isActive});
        reload();
    },
    onDuplicate: async (communication) =>
    {
        await cloneCommunication(communication.id);
        reload();
    },
    onDelete: async (communication) =>
    {
        await deleteCommunication(communication.id);
        reload();
    },
});
