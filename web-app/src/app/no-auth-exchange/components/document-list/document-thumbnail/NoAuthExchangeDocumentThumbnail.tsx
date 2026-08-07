import React from "react";
import {Skeleton, SkeletonItem} from "@fluentui/react-components";
import {DocumentRegular} from "@fluentui/react-icons";
import {downloadNoAuthExchangeDocumentThumbnail} from "../../../../../services/exchangeApi.ts";
import {DocumentBasicDto} from "../../../../models/models.tsx";
import {useNoAuthExchangeDocumentThumbnailStyles} from "./NoAuthExchangeDocumentThumbnailStyles.tsx";

interface NoAuthExchangeDocumentThumbnailProps
{
    document: DocumentBasicDto;
    exchangeId: string;
}

const NoAuthExchangeDocumentThumbnail: React.FC<NoAuthExchangeDocumentThumbnailProps> = (props) =>
{
    const styles = useNoAuthExchangeDocumentThumbnailStyles();
    const [thumbnailUrl, setThumbnailUrl] = React.useState<string | null>(null);
    const [previewUnavailable, setPreviewUnavailable] = React.useState(false);
    const handlePreviewError = () =>
    {
        setThumbnailUrl(null);
        setPreviewUnavailable(true);
    };

    React.useEffect(() =>
    {
        if (!props.exchangeId || !props.document.id || !props.document.uploadDate)
        {
            setThumbnailUrl(null);
            setPreviewUnavailable(true);
            return;
        }

        let disposed = false;
        let objectUrl: string | null = null;
        setThumbnailUrl(null);
        setPreviewUnavailable(false);

        downloadNoAuthExchangeDocumentThumbnail(props.exchangeId, props.document.id, props.document.hash)
            .then(response =>
            {
                if (disposed) return;
                objectUrl = URL.createObjectURL(new Blob([response as Blob], {type: "image/png"}));
                setThumbnailUrl(objectUrl);
            })
            .catch(() =>
            {
                if (!disposed) setPreviewUnavailable(true);
            });

        return () =>
        {
            disposed = true;
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [props.document.hash, props.document.id, props.document.uploadDate, props.exchangeId]);

    return (
        <div id={`no-auth-exchange-document-thumbnail-wrapper-${props.document.id}`}
             className={styles.thumbnailWrapper}>
            <div id={`no-auth-exchange-document-thumbnail-${props.document.id}`}
                 className={styles.thumbnailFrame}
                 aria-hidden="true">
                {!thumbnailUrl && !previewUnavailable && (
                    <Skeleton id={`no-auth-exchange-document-thumbnail-loading-${props.document.id}`}>
                        <SkeletonItem id={`no-auth-exchange-document-thumbnail-loading-item-${props.document.id}`}
                                      className={styles.thumbnailSkeleton}/>
                    </Skeleton>
                )}
                {previewUnavailable && (
                    <DocumentRegular className={styles.thumbnailPlaceholderIcon}/>
                )}
                {thumbnailUrl && (
                    <img id={`no-auth-exchange-document-thumbnail-image-${props.document.id}`}
                         src={thumbnailUrl}
                         alt=""
                         className={styles.thumbnailImage}
                         onError={handlePreviewError}/>
                )}
            </div>
        </div>
    );
};

export default NoAuthExchangeDocumentThumbnail;

