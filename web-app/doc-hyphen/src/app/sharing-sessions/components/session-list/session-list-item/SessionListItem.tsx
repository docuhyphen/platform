import React from 'react';
import {Avatar, ListItem, Text} from "@fluentui/react-components";
import {useSharingSessionStyles} from "../SessionListStyles.tsx";
import {SharingSessionBasicDto} from "../../../../models/models.tsx";
import {formatDateWithOrdinal} from "../../../../helpers.ts";

interface SessionListItemProps
{
    session: SharingSessionBasicDto;
    isSelected: boolean;
}

const SessionListItem: React.FC<SessionListItemProps> = ({session, isSelected}) =>
{
    const styles = useSharingSessionStyles();

    const listItemCard = () =>
    {
        return <div className={styles.listCard}>
            <section className={styles.listCardItem}>
                <span>
                    <Avatar name={session.recipientEmail}/>
                </span>
                <span className={styles.listCardItemDetails}>
                    <div className={`${styles.caption1} ${styles.truncatedText}`}>
                        {session.recipientEmail.length > 80
                            ? `${session.recipientEmail.substring(0, 80)}...`
                            : session.recipientEmail}
                    </div>
                    <div className={styles.listCardItemRow}>
                        <Text size={300}
                              weight={"semibold"}
                              className={styles.sessionName}>
                            {session.sessionName.length > 80
                                ? `${session.sessionName.substring(0, 80)}...`
                                : session.sessionName}
                        </Text>
                        <Text align={"end"}
                              size={100}
                              className={styles.createdDate}>
                            {formatDateWithOrdinal(session.createdDate)}
                        </Text>
                    </div>
                    <div className={styles.sessionDescription}>
                        <Text size={200}
                              italic={true}
                              className={styles.truncatedText}>
                            {session.description && session.description.length > 80
                                ? `${session.description.substring(0, 80)}...`
                                : session.description}
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
            checkmark={null}
        >
            {listItemCard()}
        </ListItem>
    );
};

export default SessionListItem;