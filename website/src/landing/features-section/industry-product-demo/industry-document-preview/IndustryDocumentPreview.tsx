import type {IndustryDocumentExample} from "../../featureContent.ts";
import {useIndustryDocumentPreviewStyles} from "./IndustryDocumentPreviewStyles.tsx";

interface IndustryDocumentPreviewProps
{
    document: IndustryDocumentExample;
}

export function IndustryDocumentPreview({document}: IndustryDocumentPreviewProps)
{
    const styles = useIndustryDocumentPreviewStyles();

    return (
        <div id={"industry-demo-document-canvas"}
             className={styles.documentCanvas}>
            <img id={"industry-demo-document-preview"}
                 className={styles.documentPreview}
                 src={document.previewSrc}
                 alt={`${document.title} preview`}
                 loading="lazy"
                 width={595}
                 height={842}/>
        </div>
    );
}
