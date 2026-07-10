import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {fetchMySecurityEvents} from "../../../../services/auditService.ts";
import {useAuditEventPage} from "../../../audit/components/use-audit-event-page/useAuditEventPage.ts";
import AuditEventTable from "../../../audit/components/audit-event-table/AuditEventTable.tsx";
import AuditEventDetail from "../../../audit/components/audit-event-detail/AuditEventDetail.tsx";
import {AuditEventDto} from "../../../models/models.tsx";
import ProfileSectionCard from "../profile-section-card/ProfileSectionCard.tsx";
import {useProfileSecurityEventsStyles} from "./ProfileSecurityEventsStyles.tsx";

/**
 * Personal "Security Activity" card (Phase 7): the signed-in user's own authentication/security
 * events from `GET /users/me/security-events`, using the shared audit table/detail components.
 */
const ProfileSecurityEvents = () =>
{
    const styles = useProfileSecurityEventsStyles();
    const [selectedEvent, setSelectedEvent] = useState<AuditEventDto | null>(null);
    const {items, loading, error, cursor, loadMore} = useAuditEventPage((params) =>
        fetchMySecurityEvents(params)
    );

    return (
        <ProfileSectionCard
            id={"profile-security-events-card"}
            title={"Security activity"}
            description={"Recent sign-ins and security-relevant activity on your account."}
        >
            <div id={"profile-security-events-content"} className={styles.container}>
                {error && <Text id={"profile-security-events-error"}>{error}</Text>}

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
        </ProfileSectionCard>
    );
};

export default ProfileSecurityEvents;
