import React from 'react';
import {Avatar, Badge, Divider, ListItem, Text} from "@fluentui/react-components";
import {useSharingSessionStyles} from "../SessionListStyles.tsx";
import {SharingSessionBasicDto, SharingSessionStatus} from "../../../../models/models.tsx";
import {formatDateWithOrdinal} from "../../../../helpers.ts";
import {SessionListTab} from "../session-list-tabs/SessionListTabs.tsx";

interface SessionListItemProps
{
    session: SharingSessionBasicDto;
    isSelected: boolean;
    activeTab: SessionListTab;
}

const SessionListItem: React.FC<SessionListItemProps> = ({session, isSelected, activeTab}) =>
{
    const styles = useSharingSessionStyles();

    const getArchiveStatusMeta = () =>
    {
        if (activeTab !== 'archive') return null;

        if (session.status === SharingSessionStatus.REJECTED)
        {
            return {
                label: 'Declined',
                className: styles.archiveStatusChipDeclined,
                appearance: 'filled' as const,
                color: 'danger' as const,
            };
        }

        return {
            label: 'Ended',
            className: styles.archiveStatusChipEnded,
            appearance: 'outline' as const,
            color: 'neutral' as const,
        };
    }

    const listItemCard = () =>
    {
        const sessionName = session.sessionName || "Untitled session";
        const archiveStatusMeta = getArchiveStatusMeta();
        const descriptionText = session.description
            ? (session.description.length > 80 ? `${session.description.substring(0, 80)}...` : session.description)
            : "\u00A0";

        return <div className={styles.listCard}>
            <section className={styles.listCardItem}>
                <span>
                    <Avatar name={session.recipientEmail}/>
                </span>
                <span className={styles.listCardItemDetails}>
                    <div className={`${styles.caption1} ${styles.truncatedText}`}>
                        {/*{session.recipientEmail.length > 80*/}
                        {/*    ? `${session.recipientEmail.substring(0, 80)}...`*/}
                        {/*    : session.recipientEmail}*/}
                    </div>
                    <div className={styles.listCardItemRow}>
                        <Text size={300}
                              weight={"semibold"}
                              className={styles.sessionName}>
                            {sessionName.length > 80
                                ? `${sessionName.substring(0, 80)}...`
                                : sessionName}
                        </Text>
                        <div className={styles.titleMetaRow}>
                            <Text align={"end"}
                                  size={100}
                                  className={styles.createdDate}>
                                {formatDateWithOrdinal(session.createdDate)}
                            </Text>
                            {archiveStatusMeta && <Divider vertical className={styles.titleMetaDivider}/>}
                            {archiveStatusMeta && (
                                <Badge
                                    appearance={archiveStatusMeta.appearance}
                                    color={archiveStatusMeta.color}
                                    className={archiveStatusMeta.className}>
                                    {archiveStatusMeta.label}
                                </Badge>
                            )}
                        </div>
                    </div>
                    <div className={styles.sessionDescription}>
                        <Text size={200}
                              italic={true}
                              className={styles.truncatedText}>
                            {descriptionText}
                        </Text>
                    </div>
                </span>
            </section>
        </div>
    }

    return (
        <ListItem
            className={isSelected ? styles.sharingSessionsListSelectedItem : styles.sharingSessionsListItem}
            key={session.id}
            value={session.id}
            data-value={session.id}
            checkmark={null}>
            {listItemCard()}
        </ListItem>
    );
};

export default SessionListItem;