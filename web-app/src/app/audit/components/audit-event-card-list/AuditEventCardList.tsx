import {Button, Spinner, Text} from "@fluentui/react-components";
import {AuditEventCursorDto, AuditEventDto} from "../../../models/models.tsx";
import {formatDateTime} from "../../../helpers.ts";
import {ArrowDownIcon, ArrowRightIcon} from "../../../components/IconBundles.tsx";
import {formatAuditActor, formatAuditEventType} from "../../auditDisplayFormatters.ts";
import AuditCategoryBadge from "../audit-category-badge/AuditCategoryBadge.tsx";
import {useAuditEventCardListStyles} from "./AuditEventCardListStyles.tsx";
import {
    Clock16Regular,
    Clock20Regular, Person16Regular,
    Tag16Filled,
    Tag16Regular,
    Tag20Regular,
    Tag24Regular, Target16Regular
} from "@fluentui/react-icons";

interface AuditEventCardListProps
{
    items: AuditEventDto[];
    nextCursor: AuditEventCursorDto | null;
    onLoadMore: () => void;
    onEventClick: (event: AuditEventDto) => void;
    loading: boolean;
}

const outcomeStyleKey = (outcome: string): "outcomeSuccess" | "outcomeFailure" | "outcomeNeutral" =>
{
    if (outcome === "SUCCESS")
    {
        return "outcomeSuccess";
    }
    if (outcome === "DENIED" || outcome === "FAILURE")
    {
        return "outcomeFailure";
    }
    return "outcomeNeutral";
};

/** Shared, paginated card-list rendering of ledger-backed audit events (list variant of AuditEventTable). */
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
            <Text id={"audit-event-card-list-empty"} className={styles.emptyText}>
                No audit events found.
            </Text>
        );
    }

    return (
        <div id={"audit-event-card-list-container"} className={styles.container}>
            <div className={styles.cardList}>
                {items.map((event) => (
                    <button
                        id={`audit-event-card-${event.eventId}`}
                        key={event.eventId}
                        type={"button"}
                        className={styles.card}
                        onClick={() => onEventClick(event)}
                    >
                        <div className={styles.cardTopRow}>
                            <div className={styles.cardTopRowLeft}>
                                <AuditCategoryBadge category={event.category}/>
                            </div>
                            <Text size={200} className={styles.occurredAt}>{formatDateTime(event.occurredAt)}</Text>
                        </div>
                        <div className={styles.cardBottomRow}>
                            <div className={styles.cardBottomRowItem}>
                                <Tag16Regular/>
                                <Text className={styles.eventTypeLabel}>{formatAuditEventType(event.eventTypeKey)}</Text>
                            </div>
                            <div className={styles.cardBottomRowItem}>
                                <Person16Regular/>
                                <Text size={200} className={styles.truncate}>
                                    {formatAuditActor(event.actorKind, event.actorLabel)}
                                </Text>
                            </div>
                        </div>
                    </button>
                ))}
            </div>

            {loading && (
                <div id={"audit-event-card-list-loading"} className={styles.footer}>
                    <Spinner size={"small"} label={"Loading audit events..."} labelPosition={"after"}/>
                </div>
            )}

            {!loading && (
                <div className={styles.footer}>
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
