import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {AuditEventDto} from "../../../models/models.tsx";
import {formatDateTime} from "../../../helpers.ts";
import {
    formatAuditActor,
    formatAuditEntityLabel,
    formatAuditEventType,
} from "../../auditDisplayFormatters.ts";
import AuditCategoryBadge from "../audit-category-badge/AuditCategoryBadge.tsx";
import AuditEventDetailCard from "./AuditEventDetailCard.tsx";
import AuditEventDetailMoreInfo from "./AuditEventDetailMoreInfo.tsx";
import {useAuditEventDetailStyles} from "./AuditEventDetailStyles.tsx";
import {
    Box24Regular,
    ChatWarning24Regular,
    Clock24Regular, Person24Regular,
    Tag24Regular,
} from "@fluentui/react-icons";

interface AuditEventDetailProps
{
    event: AuditEventDto | null;
    open: boolean;
    onDismiss: () => void;
}

/** Shared full-detail view for one ledger-backed audit event, used across every audit surface. */
const AuditEventDetail = (
    {
        event,
        open,
        onDismiss,
    }: AuditEventDetailProps
) =>
{
    const styles = useAuditEventDetailStyles();

    if (!event)
    {
        return null;
    }

    const actorValue = formatAuditActor(event.actorKind, event.actorLabel, event.actorRole);
    const actorCopyValue = [actorValue, event.actorId].filter(Boolean).join(", ");
    const targetValue = formatAuditEntityLabel(event.targetLabel, event.targetType);
    const targetCopyValue = [targetValue, event.targetId].filter(Boolean).join(", ");

    return (
        <Dialog
            open={open}
            onOpenChange={(_, data) =>
            {
                if (!data.open)
                {
                    onDismiss();
                }
            }}
        >
            <DialogSurface id={"audit-event-detail-surface"} className={styles.surface}>
                <DialogBody>
                    <DialogTitle action={<AuditCategoryBadge category={event.category}/>}>
                        Audit event detail
                    </DialogTitle>
                    <DialogContent id={"audit-event-detail-content"} className={styles.body}>
                        <div className={styles.cardList}>
                            <AuditEventDetailCard
                                id={"audit-event-detail-event-type"}
                                icon={<Tag24Regular/>}
                                label={"Event type"}
                                value={formatAuditEventType(event.eventTypeKey)}
                            />
                            <AuditEventDetailCard
                                id={"audit-event-detail-occurred-at"}
                                icon={<Clock24Regular/>}
                                label={"Occurred at"}
                                value={formatDateTime(event.occurredAt)}
                            />
                            <AuditEventDetailCard
                                id={"audit-event-detail-actor"}
                                icon={<Person24Regular/>}
                                label={"Actor"}
                                value={actorValue}
                            />
                            <AuditEventDetailCard
                                id={"audit-event-detail-target"}
                                icon={<Box24Regular/>}
                                label={"Target"}
                                value={targetValue}
                            />
                            {event.reason && (
                                <AuditEventDetailCard
                                    id={"audit-event-detail-reason"}
                                    icon={<ChatWarning24Regular/>}
                                    label={"Reason"}
                                    value={event.reason}
                                />
                            )}
                        </div>

                        <AuditEventDetailMoreInfo event={event}/>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id={"button-audit-event-detail-dismiss"}
                            appearance={"secondary"}
                            shape={"circular"}
                            onClick={onDismiss}
                        >
                            Close
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default AuditEventDetail;
