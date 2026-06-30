import React from 'react';
import {Avatar, Badge, Divider, ListItem, Text} from "@fluentui/react-components";
import {useExchangeStyles} from "../ExchangeListStyles.tsx";
import {ExchangeBasicDto, ExchangeStatus} from "../../../../models/models.tsx";
import {formatDateWithOrdinal} from "../../../../helpers.ts";
import {ExchangeListTab} from "../exchange-list-tabs/ExchangeListTabs.tsx";

interface ExchangeListItemProps
{
    exchange: ExchangeBasicDto;
    isSelected: boolean;
    activeTab: ExchangeListTab;
}

const ExchangeListItem: React.FC<ExchangeListItemProps> = ({exchange, isSelected, activeTab}) =>
{
    const styles = useExchangeStyles();

    const getArchiveStatusMeta = () =>
    {
        if (activeTab !== 'archive') return null;

        if (exchange.status === ExchangeStatus.REJECTED)
        {
            return {
                label: 'Declined',
                className: styles.archiveStatusChipDeclined,
                appearance: 'filled' as const,
                color: 'danger' as const,
            };
        }

        if (exchange.status === ExchangeStatus.RESCINDED)
        {
            return {
                label: 'Rescinded',
                className: styles.archiveStatusChipEnded,
                appearance: 'outline' as const,
                color: 'warning' as const,
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
        const name = exchange.name || "Untitled exchange";
        const archiveStatusMeta = getArchiveStatusMeta();
        const descriptionText = exchange.description
            ? (exchange.description.length > 80 ? `${exchange.description.substring(0, 80)}...` : exchange.description)
            : "\u00A0";

        return <div className={styles.listCard}>
            <section className={styles.listCardItem}>
                <span>
                    <Avatar name={exchange.recipientEmail}/>
                </span>
                <span className={styles.listCardItemDetails}>
                    <div className={`${styles.caption1} ${styles.truncatedText}`}>
                        {/*{exchange.recipientEmail.length > 80*/}
                        {/*    ? `${exchange.recipientEmail.substring(0, 80)}...`*/}
                        {/*    : exchange.recipientEmail}*/}
                    </div>
                    <div className={styles.listCardItemRow}>
                        <Text size={300}
                              weight={"semibold"}
                              className={styles.name}>
                            {name.length > 80
                                ? `${name.substring(0, 80)}...`
                                : name}
                        </Text>
                        <div className={styles.titleMetaRow}>
                            <Text align={"end"}
                                  size={100}
                                  className={styles.createdDate}>
                                {formatDateWithOrdinal(exchange.createdDate)}
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
                    <div className={styles.exchangeDescription}>
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
            className={isSelected ? styles.exchangesListSelectedItem : styles.exchangesListItem}
            key={exchange.id}
            value={exchange.id}
            data-value={exchange.id}
            checkmark={null}>
            {listItemCard()}
        </ListItem>
    );
};

export default ExchangeListItem;
