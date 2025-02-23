import React from "react";
import {Card, SkeletonItem} from "@fluentui/react-components";
import {useLandingStyles} from "../../LandingStyles.tsx";

const DocumentsSkeleton: React.FC = () =>
{
    const styles = useLandingStyles();

    return <>
        <div>
            <div>
                <SkeletonItem size={24} className={styles.skeletonSessionDocumentTitle}/>
            </div>
            <div id="documents-card-list" className={styles.documentsCardList}>
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
    </>
};

export default DocumentsSkeleton;