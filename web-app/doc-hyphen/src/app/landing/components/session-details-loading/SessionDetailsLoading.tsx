import React from "react";
import {Card, SkeletonItem} from "@fluentui/react-components";
import {useSessionDetailsLoadingStyles} from "./SessionDetailsLoadingStyles.tsx";

const SessionDetailsLoading: React.FC = () =>
{
    const styles = useSessionDetailsLoadingStyles();

    return <section className={styles.container}>
        <div className={styles.heading}>
            <div className={styles.headerLine1}>
                <div className={styles.headerLineDates}>
                    <SkeletonItem size={24} className={styles.createdDate}/>
                    <SkeletonItem size={24} className={styles.datesPipe}/>
                    <SkeletonItem size={24} className={styles.endDate}/>
                </div>
                <SkeletonItem size={16} className={styles.collapseIcon}/>
            </div>
            <div className={styles.headerLine2}>
                <SkeletonItem size={28} className={styles.sessionName}/>
                <div className={styles.sessionActions}>
                    <SkeletonItem shape="square" size={32}/>
                    <SkeletonItem shape="square" size={32}/>
                    <SkeletonItem shape="square" size={32}/>
                    <SkeletonItem shape="square" size={32} className={styles.sessionActionsMore}/>
                </div>
            </div>
            <SkeletonItem size={24} className={styles.sessionDescription}/>
        </div>
        <div className={styles.documentSearch}>
            <SkeletonItem shape="square" size={28}/>
            <SkeletonItem size={28} className={styles.documentSearchInput}/>
        </div>
        <div className={styles.documentCardListContainer}>
            <div className={styles.documentCardList}>
                {Array.from({length: 2}).map((_, index) => (
                    <Card key={index} className={styles.documentCard}>
                        <div>
                            <SkeletonItem size={20} className={styles.documentTitle}/>
                            <SkeletonItem className={styles.documentUploadDate}/>
                        </div>
                        <SkeletonItem shape="square" size={32} className={styles.documentMoreOptions}/>
                    </Card>
                ))}
            </div>
        </div>
        <div className={styles.pdfPreviewSection}>
        </div>
    </section>
};

export default SessionDetailsLoading;