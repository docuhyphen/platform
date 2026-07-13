import {IndustryDocumentPreviewActions} from "./IndustryDocumentPreviewActions.tsx";
import {useIndustryDocumentToolbarStyles} from "./IndustryDocumentToolbarStyles.tsx";

export function IndustryDocumentToolbar()
{
    const styles = useIndustryDocumentToolbarStyles();

    return (
        <div
            id="industry-demo-document-toolbar"
            className={styles.toolbar}
        >
            <IndustryDocumentPreviewActions/>
        </div>
    );
}
