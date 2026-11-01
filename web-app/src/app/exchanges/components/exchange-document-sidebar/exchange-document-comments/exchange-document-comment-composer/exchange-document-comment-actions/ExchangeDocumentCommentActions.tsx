import React from "react";
import {Button, Spinner, Text} from "@fluentui/react-components";
import {SendCommentIcon} from "../../../../../../components/IconBundles.tsx";
import {composerElementId} from "../composerElementIds.ts";
import {useExchangeDocumentCommentActionsStyles} from "./ExchangeDocumentCommentActionsStyles.tsx";

interface ExchangeDocumentCommentActionsProps
{
    idPrefix?: string;
    value: string;
    isSubmitting: boolean;
    onSubmit: () => void;
}

const ExchangeDocumentCommentActions: React.FC<ExchangeDocumentCommentActionsProps> = (
    {
        idPrefix,
        value,
        isSubmitting,
        onSubmit,
    }) =>
{
    const styles = useExchangeDocumentCommentActionsStyles();

    return (
        <div id={composerElementId(idPrefix, "comment-actions", "exchange-document-comment-actions")}
             className={styles.actions}>
            <Text id={composerElementId(idPrefix, "comment-character-count", "exchange-document-comment-character-count")}>
                {value.length} / 500
            </Text>
            <Button id={composerElementId(idPrefix, "comment-send-btn", "exchange-document-comment-send-btn")}
                    icon={isSubmitting ? <Spinner id={"exchange-document-comment-composer-spinner-1"}
                                                  size={"extra-small"}/> : <SendCommentIcon/>}
                    appearance={"transparent"}
                    shape={"circular"}
                    onClick={onSubmit}
                    size={"large"}
                    disabled={!value.trim() || isSubmitting}/>
        </div>
    );
};

export default ExchangeDocumentCommentActions;
