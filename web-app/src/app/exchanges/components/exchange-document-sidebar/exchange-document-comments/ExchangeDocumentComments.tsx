import React from "react";
import {Button, MessageBar, MessageBarActions, MessageBarBody, Spinner} from "@fluentui/react-components";
import {DocumentDetailedDto} from "../../../../models/models.tsx";
import ExchangeDocumentComment from "./exchange-document-comment/ExchangeDocumentComment.tsx";
import ExchangeDocumentCommentComposer from "./exchange-document-comment-composer/ExchangeDocumentCommentComposer.tsx";
import {useExchangeDocumentCommentsStyles} from "./ExchangeDocumentCommentsStyles.tsx";
import {useExchangeDocumentComments} from "./useExchangeDocumentComments.ts";

interface ExchangeDocumentCommentsProps
{
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    idPrefix?: string;
    pageNumber?: number;
    documentVersionId?: string;
    onNavigateToPage?: (pageNumber: number) => void;
}

const ExchangeDocumentComments: React.FC<ExchangeDocumentCommentsProps> = (props) =>
{
    const styles = useExchangeDocumentCommentsStyles();
    const state = useExchangeDocumentComments(props);
    const prefix = props.idPrefix;

    return (
        <div
            id={prefix ? `${prefix}-comments` : "exchange-document-comments"}
            className={styles.container}
        >
            {state.loadError && (
                <MessageBar
                    id={prefix ? `${prefix}-comments-load-error` : "exchange-document-comments-load-error"}
                    intent={"error"}
                >
                    <MessageBarBody>{state.loadError}</MessageBarBody>
                    <MessageBarActions
                        containerAction={
                            <Button
                                id={prefix ? `${prefix}-comments-retry` : "exchange-document-comments-retry"}
                                appearance={"transparent"}
                                shape={"circular"}
                                aria-label={"Retry loading notes and comments"}
                                onClick={() => void state.fetchComments()}
                            >
                                Retry
                            </Button>
                        }
                    />
                </MessageBar>
            )}
            <div
                id={prefix ? `${prefix}-comments-list` : "exchange-document-comments-list"}
                className={styles.list}
                ref={state.commentsListRef}
            >
                {state.loading ? (
                    <Spinner
                        id={prefix ? `${prefix}-comments-spinner` : "exchange-document-comments-spinner"}
                        size={"small"}
                    />
                ) : state.comments.length > 0 ? (
                    state.comments.map(comment => (
                        <ExchangeDocumentComment
                            key={comment.id}
                            comment={comment}
                            idPrefix={prefix}
                            onNavigateToPage={props.onNavigateToPage}
                        />
                    ))
                ) : !state.loadError && (
                    <div
                        id={prefix ? `${prefix}-comments-empty` : "exchange-document-comments-empty"}
                        className={styles.noComments}
                    >
                        No notes or comments have been added yet.
                    </div>
                )}
            </div>
            <ExchangeDocumentCommentComposer
                value={state.newComment}
                isInternal={state.isInternal}
                linkToPage={state.linkToPage}
                isSubmitting={state.addingComment}
                submitError={state.submitError}
                canPostInternal={state.canPostInternal}
                internalOrganizationName={state.internalOrganizationName}
                pageNumber={props.pageNumber}
                onValueChange={state.setNewComment}
                onInternalChange={state.setIsInternal}
                onLinkToPageChange={state.setLinkToPage}
                onSubmit={state.addComment}
                idPrefix={prefix}
            />
        </div>
    );
};

export default ExchangeDocumentComments;
