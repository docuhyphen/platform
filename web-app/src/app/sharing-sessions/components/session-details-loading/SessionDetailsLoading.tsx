import React from "react";
import {Card, SkeletonItem} from "@fluentui/react-components";
import {useSessionDetailsLoadingStyles} from "./SessionDetailsLoadingStyles.tsx";

const SessionDetailsLoading: React.FC = () =>
{
    const styles = useSessionDetailsLoadingStyles();

    return <section className={styles.container}>
        {/*
          Mirror the real SessionDetailsHeader's *default* (collapsed)
          layout: only the title row is visible. The dates row and the
          description line live inside the expandable section in the real
          component, so we don't render placeholders for them here — that
          previously made the skeleton noticeably "taller" than what loads
          in, producing a visible layout shift on first paint.

          The collapse toggle and More menu are part of the actions
          cluster in the real header, so the placeholder moves there too.
        */}
        <div className={styles.heading}>
            <div className={styles.headerLine2}>
                <SkeletonItem size={28} className={styles.sessionName}/>
                <div className={styles.sessionActions}>
                    <SkeletonItem shape="square" size={32}/>
                    <SkeletonItem shape="square" size={32}/>
                    <SkeletonItem shape="square" size={32}/>
                    <SkeletonItem shape="square" size={32} className={styles.sessionActionsMore}/>
                    <SkeletonItem shape="square" size={32} className={styles.collapseIcon}/>
                </div>
            </div>
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