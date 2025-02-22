import React from "react";
import {SkeletonItem} from "@fluentui/react-components";
import {useLandingStyles} from "../../LandingStyles.tsx";

const DetailsSkeleton: React.FC = () =>
{
    const styles = useLandingStyles();

    return <>
        <div className={styles.skeletonSessionDetails}>
            <div className={styles.skeletonDates}>
                <SkeletonItem size={16} className={styles.skeletonCreatedDate}/>
                <SkeletonItem size={16} className={styles.skeletonPipe}/>
                <SkeletonItem size={16} className={styles.skeletonEndDate}/>
            </div>
            <SkeletonItem size={28} className={styles.skeletonSessionName}/>
            <SkeletonItem size={16} className={styles.skeletonSessionDescription}/>
        </div>
        <div id="sharing-session-actions" className={styles.sharingSessionActions}>
            <SkeletonItem shape="square" size={32}/>
            <SkeletonItem shape="square" size={32}/>
            <SkeletonItem shape="square" size={32} className={styles.skeletonSessionActionsMore}/>
        </div>
    </>
};

export default DetailsSkeleton;