import React from "react";
import {Button, Checkbox, Field, mergeClasses, Spinner, Text, Textarea} from "@fluentui/react-components";
import {SendCommentIcon} from "../../../../../components/IconBundles.tsx";
import {useExchangeDocumentCommentComposerStyles} from "./ExchangeDocumentCommentComposerStyles.tsx";

interface ExchangeDocumentCommentComposerProps
{
    value: string;
    isInternal: boolean;
    isSubmitting: boolean;
    onValueChange: (value: string) => void;
    onInternalChange: (isInternal: boolean) => void;
    onSubmit: () => void;
    idPrefix?: string;
}

const ExchangeDocumentCommentComposer: React.FC<ExchangeDocumentCommentComposerProps> = (
    {
        value,
        isInternal,
        isSubmitting,
        onValueChange,
        onInternalChange,
        onSubmit,
        idPrefix,
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
            <Field
                id={idPrefix ? `${idPrefix}-comment-field` : "exchange-document-comment-field"}
                className={styles.field}
            >
                <Textarea
                    id={idPrefix ? `${idPrefix}-comment-textarea` : "textarea-exchange-document-comment"}
                    className={mergeClasses(styles.input, value.trim() ? styles.inputActive : undefined)}
                    placeholder={"Add note or comment"}
                    maxLength={255}
                    value={value}
                    onChange={(_event, data) => onValueChange(data.value)}
                    onKeyDown={handleKeyDown}
                    disabled={isSubmitting}
                />
            </Field>
            <Checkbox
                id={idPrefix ? `${idPrefix}-comment-internal-checkbox` : "exchange-document-comment-internal-checkbox"}
                label={"Internal"}
                checked={isInternal}
                onChange={(_event, data) => onInternalChange(data.checked === true)}
                disabled={isSubmitting}
            />
            <div
                id={idPrefix ? `${idPrefix}-comment-actions` : "exchange-document-comment-actions"}
                className={styles.actions}
            >
                <Text id={idPrefix ? `${idPrefix}-comment-character-count` : "exchange-document-comment-character-count"}>
                    {value.length}/255
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
