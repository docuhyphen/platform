import React from "react";
import {Checkbox} from "@fluentui/react-components";
import {composerElementId} from "../composerElementIds.ts";
import {useExchangeDocumentCommentOptionsStyles} from "./ExchangeDocumentCommentOptionsStyles.tsx";

interface ExchangeDocumentCommentOptionsProps
{
    idPrefix?: string;
    isInternal: boolean;
    linkToPage: boolean;
    isSubmitting: boolean;
    canPostInternal: boolean;
    pageNumber?: number;
    onInternalChange: (isInternal: boolean) => void;
    onLinkToPageChange: (linkToPage: boolean) => void;
}

const ExchangeDocumentCommentOptions: React.FC<ExchangeDocumentCommentOptionsProps> = (
    {
        idPrefix,
        isInternal,
        linkToPage,
        isSubmitting,
        canPostInternal,
        pageNumber,
        onInternalChange,
        onLinkToPageChange,
    }) =>
{
    const styles = useExchangeDocumentCommentOptionsStyles();

    return (
        <div id={composerElementId(idPrefix, "comment-internal-options", "exchange-document-comment-internal-options")}
             className={styles.internalOptions}>
            {pageNumber ? (
                <Checkbox id={composerElementId(idPrefix, "comment-link-page-checkbox", "exchange-document-comment-link-page-checkbox")}
                          label={`Link to page ${pageNumber}`}
                          checked={linkToPage}
                          onChange={(_event, data) => onLinkToPageChange(data.checked === true)}
                          disabled={isSubmitting}/>
            ) : <span id={"exchange-document-comment-composer-span-1"}/>}
            {canPostInternal && (
                <Checkbox id={composerElementId(idPrefix, "comment-internal-checkbox", "exchange-document-comment-internal-checkbox")}
                          label={"Internal"}
                          checked={isInternal}
                          onChange={(_event, data) => onInternalChange(data.checked === true)}
                          disabled={isSubmitting}/>
            )}
        </div>
    );
};

export default ExchangeDocumentCommentOptions;
