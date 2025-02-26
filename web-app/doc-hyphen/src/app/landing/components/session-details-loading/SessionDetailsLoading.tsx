import React from "react";
import {Card, SkeletonItem} from "@fluentui/react-components";
import {useLandingStyles} from "../../LandingStyles.tsx";

const SessionDetailsLoading: React.FC = () =>
{
    const styles = useLandingStyles();

    return <section>
        <div className={styles.skeletonSessionDetails}>
            <div className={styles.skeletonDates}>
                <SkeletonItem size={16} className={styles.skeletonCreatedDate}/>
                <SkeletonItem size={16} className={styles.skeletonPipe}/>
                <SkeletonItem size={16} className={styles.skeletonEndDate}/>
            </div>
            <SkeletonItem size={28} className={styles.skeletonSessionName}/>
            <SkeletonItem size={16} className={styles.skeletonSessionDescription}/>
        </div>
        <div className={styles.sharingSessionActions}>
            <SkeletonItem shape="square" size={32}/>
            <SkeletonItem shape="square" size={32}/>
            <SkeletonItem shape="square" size={32} className={styles.skeletonSessionActionsMore}/>
        </div>
        <div>
            <div>
                <SkeletonItem size={24} className={styles.skeletonSessionDocumentTitle}/>
            </div>
            <div className={styles.documentsCardList}>
                {Array.from({length: 10}).map((_, index) => (
                    <Card key={index} className={styles.skeletonSessionDocument}>
                        <div>
                            <SkeletonItem size={24} className={styles.skeletonSessionDocumentTitle}/>
                            <SkeletonItem className={styles.skeletonSessionDocumentUploadDate}/>
                        </div>
                        <SkeletonItem shape="square" size={32} className={styles.skeletonSessionDocumentMore}/>
                    </Card>
                ))}
            </div>
        </div>
    </section>
};

export default SessionDetailsLoading;