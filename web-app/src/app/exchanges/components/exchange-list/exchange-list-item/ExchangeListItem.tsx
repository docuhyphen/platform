import React from 'react';
import {Avatar, Badge, Divider, ListItem, mergeClasses, Text} from "@fluentui/react-components";
import {useExchangeStyles} from "../ExchangeListStyles.tsx";
import {useExchangeListItemStyles} from "./ExchangeListItemStyles.tsx";
import {ExchangeListEntry, ExchangeStatus} from "../../../../models/models.tsx";
import {formatDateWithOrdinal} from "../../../../helpers.ts";
import {ExchangeListTab} from "../exchange-list-tabs/ExchangeListTabs.tsx";

interface ExchangeListItemProps
{
    exchange: ExchangeListEntry;
    isSelected: boolean;
    activeTab: ExchangeListTab;
    staggerIndex: number;
}

const MAX_STAGGER_INDEX = 29;
const STAGGER_DELAY_MS = 50;

const ExchangeListItem: React.FC<ExchangeListItemProps> = ({exchange, isSelected, activeTab, staggerIndex}) =>
{
    const styles = useExchangeStyles();
    const itemStyles = useExchangeListItemStyles();
    const animationDelay = `${Math.min(staggerIndex, MAX_STAGGER_INDEX) * STAGGER_DELAY_MS}ms`;

    const getArchiveStatusMeta = () =>
    {
        if (activeTab !== 'archive') return null;

        if (exchange.status === ExchangeStatus.REJECTED)
        {
            return {
                label: 'Declined',
                className: itemStyles.archiveStatusChipDeclined,
                appearance: 'filled' as const,
                color: 'danger' as const,
            };
        }

        if (exchange.status === ExchangeStatus.RESCINDED)
        {
            return {
                label: 'Rescinded',
                className: itemStyles.archiveStatusChipEnded,
                appearance: 'outline' as const,
                color: 'warning' as const,
            };
        }

        return {
            label: 'Ended',
            className: itemStyles.archiveStatusChipEnded,
            appearance: 'outline' as const,
            color: 'informative' as const,
        };
    }

    const listItemCard = () =>
    {
        const name = exchange.name || "Untitled Exchange";
        const archiveStatusMeta = getArchiveStatusMeta();
        const descriptionText = exchange.description
            ? (exchange.description.length > 80 ? `${exchange.description.substring(0, 80)}...` : exchange.description)
            : "No description available";

        return <div id={"exchange-list-item-div-1"}
                    className={styles.listCard}>
            <section id={"exchange-list-item-section-1"}
                     className={styles.listCardItem}>
                <span id={"exchange-list-item-span-1"}>
                    <Avatar id={"exchange-list-item-avatar-1"}
                            name={'recipientEmail' in exchange ? exchange.recipientEmail : undefined}/>
                </span>
                <span id={"exchange-list-item-span-2"}
                      className={styles.listCardItemDetails}>
                    <div id={"exchange-list-item-div-2"}
                         className={`${itemStyles.caption1} ${itemStyles.truncatedText}`}>
                    </div>
                    <div id={"exchange-list-item-div-3"}
                         className={styles.listCardItemRow}>
                        <Text id={"exchange-list-item-text-1"}
                              size={300}
                              weight={"semibold"}
                              className={itemStyles.name}>
                            {name.length > 80
                                ? `${name.substring(0, 80)}...`
                                : name}
                        </Text>
                        <div id={"exchange-list-item-div-4"}
                             className={itemStyles.titleMetaRow}>
                            <Text id={"exchange-list-item-text-2"}
                                  align={"end"}
                                  size={100}
                                  className={itemStyles.createdDate}>
                                {formatDateWithOrdinal(exchange.createdDate)}
                            </Text>
                            {archiveStatusMeta && <Divider id={"exchange-list-item-divider-1"}
                                                           vertical
                                                           className={itemStyles.titleMetaDivider}/>}
                            {archiveStatusMeta && (
                                <Badge
                                    id={"exchange-list-item-badge-1"}
                                    appearance={archiveStatusMeta.appearance}
                                    color={archiveStatusMeta.color}
                                    className={archiveStatusMeta.className}>
                                    {archiveStatusMeta.label}
                                </Badge>
                            )}
                        </div>
                    </div>
                    <div id={"exchange-list-item-div-5"}
                         className={itemStyles.exchangeDescription}>
                        <Text id={"exchange-list-item-text-3"}
                              size={200}
                              italic={true}
                              className={itemStyles.truncatedText}>
                            {descriptionText}
                        </Text>
                    </div>
                </span>
            </section>
        </div>
    }

    return (
        <ListItem
            id={`exchange-list-item-${exchange.id}`}
            className={mergeClasses(
                itemStyles.exchangeListItemEnter,
                isSelected ? styles.exchangesListSelectedItem : itemStyles.exchangesListItem
            )}
            key={exchange.id}
            value={exchange.id}
            data-value={exchange.id}
            style={{animationDelay}}
            checkmark={null}>
            {listItemCard()}
        </ListItem>
    );
};

export default ExchangeListItem;
