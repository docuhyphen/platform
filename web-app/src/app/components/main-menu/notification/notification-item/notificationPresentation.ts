import {NotificationDto, NotificationType} from '../../../../models/models.tsx';

const TECHNICAL_SUBJECT_PATTERN = /^Subject:\s+[A-Z][A-Z0-9_]*\s+\S+/;

export function getNotificationTitle(type: NotificationType | string): string
{
    switch (type)
    {
        case NotificationType.NEW_COMMENT:
            return 'New Comment';
        case NotificationType.NEW_SESSION:
            return 'New Session';
        case NotificationType.DOCUMENT_ADDED:
            return 'Document Added';
        case NotificationType.DOCUMENT_UPDATED:
            return 'Document Updated';
        case NotificationType.EXCHANGE_ENDED:
            return 'Exchange Ended';
        case NotificationType.EXCHANGE_INITIATED:
        case 'exchange.initiated':
            return 'Exchange Initiated';
        case 'exchange.accepted':
            return 'Exchange Accepted';
        case 'exchange.declined':
            return 'Exchange Declined';
        case 'exchange.ended':
            return 'Exchange Ended';
        case 'document.commented':
            return 'Document Comment';
        case 'document.deleted':
            return 'Document Deleted';
        case 'document.added':
            return 'Document Added';
        case 'document.uploaded':
            return 'Document Uploaded';
        case 'workflow.step_assigned':
        case 'WORKFLOW_STEP_ASSIGNED':
            return 'Approval Required';
        case 'workflow.escalated':
        case 'WORKFLOW_ESCALATED':
            return 'Approval Escalated';
        case 'session.activated':
        case 'EXCHANGE_ACTIVATED':
            return 'Exchange Approved';
        case 'session.rejected':
        case 'EXCHANGE_REJECTED':
            return 'Exchange Rejected';
        default:
            return 'Notification';
    }
}

export function getNotificationMessage(notification: NotificationDto): string
{
    const exchangeName = notification.data.exchangeName?.trim();
    const initiatorName = notification.data.initiatorName?.trim();
    switch (notification.type)
    {
        case 'workflow.step_assigned':
        case 'WORKFLOW_STEP_ASSIGNED':
            if (exchangeName && initiatorName)
            {
                return `${initiatorName} requested your approval for Exchange "${exchangeName}".`;
            }
            if (exchangeName)
            {
                return `Review and decide on the approval request for Exchange "${exchangeName}".`;
            }
            return initiatorName
                ? `${initiatorName} requested your approval.`
                : 'Review and decide on the pending approval.';
        case 'workflow.escalated':
        case 'WORKFLOW_ESCALATED':
            return exchangeName
                ? `An overdue approval for Exchange "${exchangeName}" has been assigned to you.`
                : 'An overdue approval has been assigned to you for review.';
        case 'document.commented':
        case 'NEW_COMMENT':
            return getDocumentCommentMessage(notification, exchangeName);
        default:
            if (TECHNICAL_SUBJECT_PATTERN.test(notification.message))
            {
                return notification.exchangeId || notification.data.subjectType === 'EXCHANGE'
                    ? 'Open the related Exchange to view more information.'
                    : 'Open this notification to view more information.';
            }
            return notification.message;
    }
}

function getDocumentCommentMessage(notification: NotificationDto, exchangeName?: string): string
{
    const commenterName = notification.data.commenterName?.trim();
    const documentName = notification.data.documentName?.trim();
    if (commenterName && documentName && exchangeName)
    {
        return `${commenterName} commented on "${documentName}" in Exchange "${exchangeName}".`;
    }
    if (commenterName && documentName)
    {
        return `${commenterName} commented on "${documentName}" in an Exchange shared with you.`;
    }
    if (commenterName && exchangeName)
    {
        return `${commenterName} commented on a document in Exchange "${exchangeName}".`;
    }
    const legacyCommenter = notification.message
        .match(/^(.+?)\s+(?:added|made) a comment (?:to|on) a document\.?$/i)?.[1]?.trim();
    const author = commenterName || legacyCommenter;
    return author
        ? `${author} added a document comment. Open it to view the document and Exchange.`
        : 'A document comment was added. Open it to view the document and Exchange.';
}
