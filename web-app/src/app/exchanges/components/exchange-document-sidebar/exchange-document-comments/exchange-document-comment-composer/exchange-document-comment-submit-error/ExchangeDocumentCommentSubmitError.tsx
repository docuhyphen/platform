import React from "react";
import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";
import {composerElementId} from "../composerElementIds.ts";

interface ExchangeDocumentCommentSubmitErrorProps
{
    idPrefix?: string;
    submitError: string;
    isSubmitting: boolean;
    onRetry: () => void;
}

const ExchangeDocumentCommentSubmitError: React.FC<ExchangeDocumentCommentSubmitErrorProps> = (
    {
        idPrefix,
        submitError,
        isSubmitting,
        onRetry,
    }) =>
{
    return (
        <MessageBar id={composerElementId(idPrefix, "comment-submit-error", "exchange-document-comment-submit-error")}
                    intent={"error"}>
            <MessageBarBody id={"exchange-document-comment-composer-message-bar-body-1"}>{submitError}</MessageBarBody>
            <MessageBarActions id={"exchange-document-comment-composer-message-bar-actions-1"}
                               containerAction={
                                   <Button id={composerElementId(idPrefix, "comment-submit-retry", "exchange-document-comment-submit-retry")}
                                           appearance={"transparent"}
                                           shape={"circular"}
                                           disabled={isSubmitting}
                                           onClick={onRetry}>
                                       Retry
                                   </Button>
                               }/>
        </MessageBar>
    );
};

export default ExchangeDocumentCommentSubmitError;
