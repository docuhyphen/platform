import {useEffect, useState} from "react";
import {Text} from "@fluentui/react-components";
import {AuditEventDto} from "../../models/models.tsx";
import {fetchOrganizationAuditEvents, fetchPlatformAuditEvents} from "../../../services/auditService.ts";
import {useAuditEventPage} from "../components/use-audit-event-page/useAuditEventPage.ts";
import AuditEventTable from "../components/audit-event-table/AuditEventTable.tsx";
import AuditEventDetail from "../components/audit-event-detail/AuditEventDetail.tsx";
import {AuditScope} from "../auditScope.ts";
import {dateOnlyToRangeEndInstant, dateOnlyToRangeStartInstant} from "../auditDateRange.ts";
import {useAuditEventsSectionStyles} from "./AuditEventsSectionStyles.tsx";
import AuditEventsFilters from "./audit-events-filters/AuditEventsFilters.tsx";

interface AuditEventsSectionProps
{
    scope: AuditScope;
}

/** Search/filter + paginated results section of the Audit workspace. */
const AuditEventsSection = (
    {
        scope,
    }: AuditEventsSectionProps
) =>
{
    const styles = useAuditEventsSectionStyles();
    const [categories, setCategories] = useState<string[]>([]);
    const [occurredAfter, setOccurredAfter] = useState<string>("");
    const [occurredBefore, setOccurredBefore] = useState<string>("");
    const [selectedEvent, setSelectedEvent] = useState<AuditEventDto | null>(null);

    const {items, loading, error, cursor, loadMore, reset} = useAuditEventPage((params) =>
    {
        const occurredAfterInstant = occurredAfter ? dateOnlyToRangeStartInstant(occurredAfter) : undefined;
        const occurredBeforeInstant = occurredBefore ? dateOnlyToRangeEndInstant(occurredBefore) : undefined;

        return scope.kind === "organization"
            ? fetchOrganizationAuditEvents(scope.organizationId, {
                ...params,
                categories,
                occurredAfter: occurredAfterInstant,
                occurredBefore: occurredBeforeInstant,
            })
            : fetchPlatformAuditEvents({
                ...params,
                categories,
                occurredAfter: occurredAfterInstant,
                occurredBefore: occurredBeforeInstant,
            });
    });

    useEffect(() =>
    {
        reset();
        // Re-search whenever the filter selection changes; `reset` itself is stable per hook.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [categories, occurredAfter, occurredBefore, scope]);

    return (
        <div id={"audit-events-section"} className={styles.container}>
            <AuditEventsFilters
                categories={categories}
                occurredAfter={occurredAfter}
                occurredBefore={occurredBefore}
                onCategoriesChange={setCategories}
                onOccurredAfterChange={setOccurredAfter}
                onOccurredBeforeChange={setOccurredBefore}
            />

            {error && <Text id={"audit-events-error"} className={styles.errorText}>{error}</Text>}

            <AuditEventTable
                items={items}
                nextCursor={cursor}
                onLoadMore={loadMore}
                onEventClick={setSelectedEvent}
                loading={loading}
            />

            <AuditEventDetail
                event={selectedEvent}
                open={selectedEvent !== null}
                onDismiss={() => setSelectedEvent(null)}
            />
        </div>
    );
};

export default AuditEventsSection;
