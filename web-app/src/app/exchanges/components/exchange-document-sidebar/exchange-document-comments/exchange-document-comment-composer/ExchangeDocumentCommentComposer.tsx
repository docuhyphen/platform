import React from "react";
import {Field, mergeClasses, Textarea} from "@fluentui/react-components";
import {useExchangeDocumentCommentComposerStyles} from "./ExchangeDocumentCommentComposerStyles.tsx";
import InternalNoteVisibilityBadge from "./internal-note-visibility-badge/InternalNoteVisibilityBadge.tsx";
import ExchangeDocumentCommentSubmitError
    from "./exchange-document-comment-submit-error/ExchangeDocumentCommentSubmitError.tsx";
import ExchangeDocumentCommentOptions from "./exchange-document-comment-options/ExchangeDocumentCommentOptions.tsx";
import ExchangeDocumentCommentActions from "./exchange-document-comment-actions/ExchangeDocumentCommentActions.tsx";
import {composerElementId} from "./composerElementIds.ts";

interface ExchangeDocumentCommentComposerProps
{
    value: string;
    isInternal: boolean;
    linkToPage: boolean;
    isSubmitting: boolean;
    onValueChange: (value: string) => void;
    onInternalChange: (isInternal: boolean) => void;
    onLinkToPageChange: (linkToPage: boolean) => void;
    onSubmit: () => void;
    idPrefix?: string;
    submitError?: string | null;
    canPostInternal?: boolean;
    internalOrganizationName?: string;
    pageNumber?: number;
}

const ExchangeDocumentCommentComposer: React.FC<ExchangeDocumentCommentComposerProps> = (
    {
        value,
        isInternal,
        linkToPage,
        isSubmitting,
        onValueChange,
        onInternalChange,
        onLinkToPageChange,
        onSubmit,
        idPrefix,
        submitError,
        canPostInternal = false,
        internalOrganizationName,
        pageNumber,
    }) =>
{
    const styles = useExchangeDocumentCommentComposerStyles();

    const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) =>
    {
        if (event.key === "Enter" && !event.shiftKey)
        {
            event.preventDefault();
            onSubmit();
        }
    };

    return (
        <div id={composerElementId(idPrefix, "comment-composer", "exchange-document-comment-composer")}
             className={styles.container}>
            <div id={composerElementId(idPrefix, "comment-input-container", "exchange-document-comment-input-container")}
                 className={styles.inputContainer}>
                <Field id={composerElementId(idPrefix, "comment-field", "exchange-document-comment-field")}
                       className={styles.field}>
                    <Textarea id={composerElementId(idPrefix, "comment-textarea", "textarea-exchange-document-comment")}
                              className={mergeClasses(
                                  styles.input,
                                  value.trim() ? styles.inputActive : undefined,
                                  isInternal ? styles.inputInternal : undefined,
                              )}
                              placeholder={"Add note or comment"}
                              maxLength={500}
                              value={value}
                              onChange={(_event, data) => onValueChange(data.value)}
                              onKeyDown={handleKeyDown}
                              disabled={isSubmitting}/>
                </Field>
                {isInternal && (
                    <InternalNoteVisibilityBadge id={composerElementId(idPrefix, "comment-internal-help", "exchange-document-comment-internal-help")}
                                                 organizationName={internalOrganizationName}/>
                )}
            </div>
            {submitError && (
                <ExchangeDocumentCommentSubmitError idPrefix={idPrefix}
                                                    submitError={submitError}
                                                    isSubmitting={isSubmitting}
                                                    onRetry={onSubmit}/>
            )}
            {(canPostInternal || pageNumber) && (
                <ExchangeDocumentCommentOptions idPrefix={idPrefix}
                                                isInternal={isInternal}
                                                linkToPage={linkToPage}
                                                isSubmitting={isSubmitting}
                                                canPostInternal={canPostInternal}
                                                pageNumber={pageNumber}
                                                onInternalChange={onInternalChange}
                                                onLinkToPageChange={onLinkToPageChange}/>
            )}
            <ExchangeDocumentCommentActions idPrefix={idPrefix}
                                            value={value}
                                            isSubmitting={isSubmitting}
                                            onSubmit={onSubmit}/>
        </div>
    );
};

export default ExchangeDocumentCommentComposer;
