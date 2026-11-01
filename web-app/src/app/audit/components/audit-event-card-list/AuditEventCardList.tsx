import {Button, Spinner, Text} from "@fluentui/react-components";
import {AuditEventCursorDto, AuditEventDto} from "../../../models/models.tsx";
import {formatDateTime} from "../../../helpers.ts";
import {formatAuditActor, formatAuditEventType} from "../../auditDisplayFormatters.ts";
import AuditCategoryBadge from "../audit-category-badge/AuditCategoryBadge.tsx";
import {useAuditEventCardListStyles} from "./AuditEventCardListStyles.tsx";
import {Person16Regular, Tag16Regular} from "@fluentui/react-icons";

interface AuditEventCardListProps
{
    items: AuditEventDto[];
    nextCursor: AuditEventCursorDto | null;
    onLoadMore: () => void;
    onEventClick: (event: AuditEventDto) => void;
    loading: boolean;
}

const AuditEventCardList = (
    {
        items,
        nextCursor,
        onLoadMore,
        onEventClick,
        loading,
    }: AuditEventCardListProps
) =>
{
    const styles = useAuditEventCardListStyles();

    if (!loading && items.length === 0)
    {
        return (
            <Text id={"audit-event-card-list-empty"}
                  className={styles.emptyText}>
                No audit events found.
            </Text>
        );
    }

    return (
        <div id={"audit-event-card-list-container"}
             className={styles.container}>
            <div id={"audit-event-card-list-div-1"}
                 className={styles.cardList}>
                {items.map((event) => (
                    <Button
                        id={`audit-event-card-${event.eventId}`}
                        key={event.eventId}
                        appearance={"transparent"}
                        shape={"circular"}
                        className={styles.card}
                        onClick={() => onEventClick(event)}
                    >
                        <div id={"audit-event-card-list-div-2"}
                             className={styles.cardTopRow}>
                            <div id={"audit-event-card-list-div-3"}
                                 className={styles.cardTopRowLeft}>
                                <AuditCategoryBadge category={event.category}/>
                            </div>
                            <Text id={"audit-event-card-list-text-1"}
                                  size={200}
                                  className={styles.occurredAt}>{formatDateTime(event.occurredAt)}</Text>
                        </div>
                        <div id={"audit-event-card-list-div-4"}
                             className={styles.cardBottomRow}>
                            <div id={"audit-event-card-list-div-5"}
                                 className={styles.cardBottomRowItem}>
                                <Tag16Regular/>
                                <Text id={"audit-event-card-list-text-2"}
                                      className={styles.eventTypeLabel}>{formatAuditEventType(event.eventTypeKey)}</Text>
                            </div>
                            <div id={"audit-event-card-list-div-6"}
                                 className={styles.cardBottomRowItem}>
                                <Person16Regular/>
                                <Text id={"audit-event-card-list-text-3"}
                                      size={200}
                                      className={styles.truncate}>
                                    {formatAuditActor(event.actorKind, event.actorLabel)}
                                </Text>
                            </div>
                        </div>
                    </Button>
                ))}
            </div>

            {loading && (
                <div id={"audit-event-card-list-loading"}
                     className={styles.footer}>
                    <Spinner id={"audit-event-card-list-spinner-1"}
                             size={"small"}
                             label={"Loading audit events..."}
                             labelPosition={"after"}/>
                </div>
            )}

            {!loading && (
                <div id={"audit-event-card-list-div-7"}
                     className={styles.footer}>
                    <Button
                        id={"button-audit-event-card-list-load-more"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={!nextCursor}
                        onClick={onLoadMore}
                    >
                        Load more
                    </Button>
                </div>
            )}
        </div>
    );
};

export default AuditEventCardList;
