import React from "react";
import {
    Button,
    Checkbox,
    Field,
    mergeClasses,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Spinner,
    Text,
    Textarea,
} from "@fluentui/react-components";
import {SendCommentIcon} from "../../../../../components/IconBundles.tsx";
import {useExchangeDocumentCommentComposerStyles} from "./ExchangeDocumentCommentComposerStyles.tsx";
import InternalNoteVisibilityBadge from "./internal-note-visibility-badge/InternalNoteVisibilityBadge.tsx";
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
        <div
            id={idPrefix ? `${idPrefix}-comment-composer` : "exchange-document-comment-composer"}
            className={styles.container}
        >
            <div
                id={idPrefix ? `${idPrefix}-comment-input-container` : "exchange-document-comment-input-container"}
                className={styles.inputContainer}
            >
                <Field
                    id={idPrefix ? `${idPrefix}-comment-field` : "exchange-document-comment-field"}
                    className={styles.field}
                >
                    <Textarea
                        id={idPrefix ? `${idPrefix}-comment-textarea` : "textarea-exchange-document-comment"}
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
                        disabled={isSubmitting}
                    />
                </Field>
                {isInternal && (
                    <InternalNoteVisibilityBadge
                        id={idPrefix ? `${idPrefix}-comment-internal-help` : "exchange-document-comment-internal-help"}
                        organizationName={internalOrganizationName}
                    />
                )}
            </div>
            {submitError && (
                <MessageBar
                    id={idPrefix ? `${idPrefix}-comment-submit-error` : "exchange-document-comment-submit-error"}
                    intent={"error"}
                >
                    <MessageBarBody>{submitError}</MessageBarBody>
                    <MessageBarActions
                        containerAction={
                            <Button
                                id={idPrefix ? `${idPrefix}-comment-submit-retry` : "exchange-document-comment-submit-retry"}
                                appearance={"transparent"}
                                shape={"circular"}
                                disabled={isSubmitting}
                                onClick={onSubmit}
                            >
                                Retry
                            </Button>
                        }
                    />
                </MessageBar>
            )}
            {(canPostInternal || pageNumber) && (
                <div
                    id={idPrefix ? `${idPrefix}-comment-internal-options` : "exchange-document-comment-internal-options"}
                    className={styles.internalOptions}
                >
                    {pageNumber ? (
                        <Checkbox
                            id={idPrefix ? `${idPrefix}-comment-link-page-checkbox` : "exchange-document-comment-link-page-checkbox"}
                            label={`Link to page ${pageNumber}`}
                            checked={linkToPage}
                            onChange={(_event, data) => onLinkToPageChange(data.checked === true)}
                            disabled={isSubmitting}
                        />
                    ) : <span/>}
                    {canPostInternal && (
                        <Checkbox
                            id={idPrefix ? `${idPrefix}-comment-internal-checkbox` : "exchange-document-comment-internal-checkbox"}
                            label={"Internal"}
                            checked={isInternal}
                            onChange={(_event, data) => onInternalChange(data.checked === true)}
                            disabled={isSubmitting}
                        />
                    )}
                </div>
            )}
            <div
                id={idPrefix ? `${idPrefix}-comment-actions` : "exchange-document-comment-actions"}
                className={styles.actions}
            >
                <Text id={idPrefix ? `${idPrefix}-comment-character-count` : "exchange-document-comment-character-count"}>
                    {value.length} / 500
                </Text>
                <Button
                    id={idPrefix ? `${idPrefix}-comment-send-btn` : "exchange-document-comment-send-btn"}
                    icon={isSubmitting ? <Spinner size={"extra-small"}/> : <SendCommentIcon/>}
                    appearance={"transparent"}
                    shape={"circular"}
                    onClick={onSubmit}
                    size={"large"}
                    disabled={!value.trim() || isSubmitting}
                />
            </div>
        </div>
    );
};

export default ExchangeDocumentCommentComposer;
