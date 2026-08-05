import React, {useEffect, useRef, useState} from "react";
import {Spinner} from "@fluentui/react-components";
import ExchangeDocumentComment from "./exchange-document-comment/ExchangeDocumentComment";
import {useExchangeDocumentCommentsStyles} from "./ExchangeDocumentCommentsStyles.tsx";
import {DocumentCommentDetailedDto, DocumentDetailedDto} from "../../../../models/models.tsx";
import {DocumentCommentService} from "../../../../../services/DocumentCommentService.tsx";
import ExchangeDocumentCommentComposer from
    "./exchange-document-comment-composer/ExchangeDocumentCommentComposer.tsx";
import {useNotifications} from "../../../../../context/NotificationContext.tsx";

interface ExchangeDocumentCommentsProps
{
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    idPrefix?: string;
}
const ExchangeDocumentComments: React.FC<ExchangeDocumentCommentsProps> = (
    {
        exchangeId,
        exchangeDocument,
        idPrefix,
    }) =>
{
    const [loading, setLoading] = useState<boolean>(true);
    const [comments, setComments] = useState<DocumentCommentDetailedDto[]>([]);
    const [newComment, setNewComment] = useState<string>("");
    const [isInternal, setIsInternal] = useState<boolean>(false);
    const [addingComment, setAddingComment] = useState<boolean>(false);
    const commentService = new DocumentCommentService();
    const styles = useExchangeDocumentCommentsStyles();
    const commentsListRef = useRef<HTMLDivElement>(null);
    const {markMatchingAsRead} = useNotifications();
    const fetchComments = async () =>
    {
        try
        {
            setLoading(true);
            const fetchedComments = await commentService.getComments(exchangeId, exchangeDocument.id);
            setComments(fetchedComments);
            markMatchingAsRead({
                eventTypes: ["document.commented"],
                data: {
                    exchangeId,
                    documentId: exchangeDocument.id,
                },
            });
        }
        catch (error)
        {
            console.error("Failed to fetch comments:", error);
        }
        finally
        {
            setLoading(false);
        }
    };
    const onAddComment = async () =>
    {
        if (addingComment)
        {
            return;
        }

        if (!newComment.trim())
        {
            return;
        }

        setAddingComment(true);

        try
        {
            const addedComment = await commentService.addComment(
                exchangeId,
                exchangeDocument.id,
                newComment,
                isInternal
            );

            setComments(currentComments => [addedComment, ...currentComments]);
            setNewComment("");
            setIsInternal(false);

            if (commentsListRef.current)
            {
                commentsListRef.current.scrollTop = 0;
            }
        }
        catch (error)
        {
            console.error("Failed to add note:", error);
        }
        finally
        {
            setAddingComment(false);
        }
    };

    useEffect(() =>
    {
        if (exchangeDocument?.id)
        {
            fetchComments();
        }
    }, [exchangeDocument?.id]);

    return (
        <div
            id={idPrefix ? `${idPrefix}-comments` : "exchange-document-comments"}
            className={styles.container}
        >
            <div
                id={idPrefix ? `${idPrefix}-comments-list` : "exchange-document-comments-list"}
                className={styles.list}
                ref={commentsListRef}
            >
                {loading ? (
                    <Spinner
                        id={idPrefix ? `${idPrefix}-comments-spinner` : "exchange-document-comments-spinner"}
                        size={"small"}
                    />
                ) : comments.length > 0 ? (
                    comments.map(comment => (
                        <ExchangeDocumentComment
                            key={comment.id}
                            comment={comment}
                            idPrefix={idPrefix}
                        />
                    ))
                ) : (
                    <div
                        id={idPrefix ? `${idPrefix}-comments-empty` : "exchange-document-comments-empty"}
                        className={styles.noComments}
                    >
                        No notes or comments have been added yet.
                    </div>
                )}
            </div>

            <ExchangeDocumentCommentComposer
                value={newComment}
                isInternal={isInternal}
                isSubmitting={addingComment}
                onValueChange={setNewComment}
                onInternalChange={setIsInternal}
                onSubmit={onAddComment}
                idPrefix={idPrefix}
            />
        </div>
    );
};

export default ExchangeDocumentComments;
