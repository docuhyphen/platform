import React from 'react';
import {ListItem, SkeletonItem} from "@fluentui/react-components";
import {useExchangeStyles} from "../ExchangeListStyles.tsx";
import {useExchangeListSkeletonStyles} from "./ExchangeListSkeletonStyles.tsx";

interface ExchangeListSkeletonProps
{
    count: number;
}

const ExchangeListSkeleton: React.FC<ExchangeListSkeletonProps> = ({count}) =>
{
    const styles = useExchangeStyles();
    const skeletonStyles = useExchangeListSkeletonStyles();

    const listItemCardSkeleton = () =>
    {
        return <div id={"exchange-list-skeleton-div-1"}
                    className={styles.listCard}>
            <section id={"exchange-list-skeleton-section-1"}
                     className={styles.listCardItem}>
                <span id={"exchange-list-skeleton-span-1"}>
                    <SkeletonItem id={"exchange-list-skeleton-skeleton-item-1"}
                                  shape="circle"
                                  size={36}/>
                </span>
                <span id={"exchange-list-skeleton-span-2"}
                      className={styles.listCardItemDetails}>
                    <SkeletonItem id={"exchange-list-skeleton-skeleton-item-2"}
                                  size={12}
                                  className={skeletonStyles.skeletonRecipientEmail}/>
                    <div id={"exchange-list-skeleton-div-2"}
                         className={styles.listCardItemRow}>
                        <SkeletonItem id={"exchange-list-skeleton-skeleton-item-3"}
                                      size={20}
                                      className={skeletonStyles.skeletonExchangeName}/>
                        <SkeletonItem id={"exchange-list-skeleton-skeleton-item-4"}
                                      size={16}
                                      className={skeletonStyles.skeletonCreatedDate}/>
                    </div>
                    <div id={"exchange-list-skeleton-div-3"}>
                        <SkeletonItem id={"exchange-list-skeleton-skeleton-item-5"}
                                      size={16}
                                      className={skeletonStyles.skeletonExchangeDescription}/>
                    </div>
                </span>
            </section>
        </div>
    };

    return (
        <>
            {Array.from({length: count}).map((_, index) => (
                <ListItem
                    id={`exchange-list-skeleton-list-item-1-${index}`}
                    className={index === 0 ? styles.exchangesListSelectedItem : ""}
                    key={index}
                    value={index.toString()}
                    data-value={index.toString()}
                    checkmark={null}
                >
                    {listItemCardSkeleton()}
                </ListItem>
            ))}
        </>
    );
};

export default ExchangeListSkeleton;