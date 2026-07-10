import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Text,
} from "@fluentui/react-components";
import {ClockIcon, CommentIcon, DismissIcon, PersonIcon, TagIcon, TargetIcon} from "../../../components/IconBundles.tsx";
import {AuditEventDto} from "../../../models/models.tsx";
import {formatDateTime} from "../../../helpers.ts";
import {getAuditEventTypeLabel} from "../../auditEventTypeLabels.ts";
import AuditCategoryBadge from "../audit-category-badge/AuditCategoryBadge.tsx";
import AuditEventDetailCard from "./AuditEventDetailCard.tsx";
import AuditEventDetailField from "./AuditEventDetailField.tsx";
import {useAuditEventDetailStyles} from "./AuditEventDetailStyles.tsx";
import {
    ChatWarning24Filled, ChatWarning24Regular,
    Clock24Filled,
    Clock24Regular,
    ClockFilled, Person24Regular,
    PersonRegular,
    Tag24Filled,
    Tag24Regular,
    TagFilled, Target24Regular, TargetFilled
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

    const payloadEntries = Object.entries(event.payload ?? {});
    const actorValue = `${event.actorLabel ?? event.actorKind}${event.actorRole ? ` - ${event.actorRole}` : ""}`;
    const actorCopyValue = [event.actorLabel ?? event.actorKind, event.actorId].filter(Boolean).join(", ");
    const targetValue = event.targetLabel ?? event.targetType ?? "-";
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
                                value={getAuditEventTypeLabel(event.eventTypeKey)}
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
                                copyValue={actorCopyValue}
                            />
                            <AuditEventDetailCard
                                id={"audit-event-detail-target"}
                                icon={<Target24Regular/>}
                                label={"Target"}
                                value={targetValue}
                                copyValue={targetCopyValue}
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

                        <Accordion id={"audit-event-detail-more-info"} collapsible>
                            <AccordionItem value={"more-info"}>
                                <AccordionHeader>More info</AccordionHeader>
                                <AccordionPanel>
                                    <div className={styles.fieldGrid}>
                                        <AuditEventDetailField label={"Outcome"} value={event.outcome}/>
                                        {event.organizationLabel && (
                                            <AuditEventDetailField label={"Organization"} value={event.organizationLabel}/>
                                        )}
                                        <AuditEventDetailField label={"Event type key"} value={event.eventTypeKey}/>
                                        <AuditEventDetailField label={"Ledger time"} value={formatDateTime(event.ledgerTime)}/>
                                        <AuditEventDetailField label={"Event hash"} value={event.eventHash}/>
                                        {event.prevHash && (
                                            <AuditEventDetailField label={"Previous hash"} value={event.prevHash}/>
                                        )}
                                    </div>

                                    {payloadEntries.length > 0 && (
                                        <div id={"audit-event-detail-payload"}>
                                            <Text size={200} className={styles.fieldLabel}>Payload</Text>
                                            <div className={styles.payloadList}>
                                                {payloadEntries.map(([key, value]) => (
                                                    <div key={key} className={styles.payloadRow}>
                                                        <Text weight={"semibold"}>{key}:</Text>
                                                        <Text>{value}</Text>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    <Text id={"audit-event-detail-redaction-note"} size={200} className={styles.note}>
                                        Some fields may be masked or withheld based on your access.
                                    </Text>
                                </AccordionPanel>
                            </AccordionItem>
                        </Accordion>
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
