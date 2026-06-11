import React from 'react';
import {ListItem, SkeletonItem} from "@fluentui/react-components";
import {useExchangeStyles} from "../ExchangeListStyles.tsx";

interface ExchangeListSkeletonProps
{
    count: number;
}

const ExchangeListSkeleton: React.FC<ExchangeListSkeletonProps> = ({count}) =>
{
    const styles = useExchangeStyles();

    const listItemCardSkeleton = () =>
    {
        return <div className={styles.listCard}>
            <section className={styles.listCardItem}>
                <span>
                    <SkeletonItem shape="circle" size={36}/>
                </span>
                <span className={styles.listCardItemDetails}>
                    <SkeletonItem size={12} className={styles.skeletonRecipientEmail}/>
                    <div className={styles.listCardItemRow}>
                        <SkeletonItem size={20} className={styles.skeletonExchangeName}/>
                        <SkeletonItem size={16} className={styles.skeletonCreatedDate}/>
                    </div>
                    <div>
                        <SkeletonItem size={16} className={styles.skeletonExchangeDescription}/>
                    </div>
                </span>
            </section>
        </div>
    };

    return (
        <>
            {Array.from({length: count}).map((_, index) => (
                <ListItem
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