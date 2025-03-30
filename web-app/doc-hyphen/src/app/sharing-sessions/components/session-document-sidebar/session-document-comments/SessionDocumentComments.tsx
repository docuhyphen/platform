import React, {useEffect, useState} from "react";
import {Button, Field, Spinner, Textarea} from "@fluentui/react-components";
import SessionDocumentComment from "../session-document-comment/SessionDocumentComment";
import {useSessionDocumentCommentsStyles} from "./SessionDocumentCommentsStyles.tsx";
import {DocumentCommentDetailedDto, DocumentDetailedDto} from "../../../../models/models.tsx";
import {DocumentCommentService} from "../../../../../services/DocumentCommentService.tsx";
import {SendCommentIcon} from "../../../../components/IconBundles.tsx";

interface SessionDocumentCommentsProps
{
    sessionId: string;
    sessionDocument: DocumentDetailedDto;
    currentUserEmail: string;
}

const SessionDocumentComments: React.FC<SessionDocumentCommentsProps> = (
    {
        sessionId,
        sessionDocument,
        currentUserEmail
    }) =>
{
    const [loading, setLoading] = useState<boolean>(true);
    const [comments, setComments] = useState<DocumentCommentDetailedDto[]>([]);
    const [newComment, setNewComment] = useState<string>("");
    const [submitting, setSubmitting] = useState<boolean>(false);
    const commentService = new DocumentCommentService();
    const styles = useSessionDocumentCommentsStyles();

    const fetchComments = async () =>
    {
        try
        {
            setLoading(true);
            const fetchedComments = await commentService.getComments(sessionId, sessionDocument.id);
            setComments(fetchedComments);
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

    const handleAddComment = async () =>
    {
        if (!newComment.trim()) return;

        try
        {
            setSubmitting(true);
            const addedComment = await commentService.addComment(
                sessionId,
                sessionDocument.id,
                newComment,
                currentUserEmail
            );
            setComments([...comments, addedComment]);
            setNewComment("");
        }
        catch (error)
        {
            console.error("Failed to add comment:", error);
        }
        finally
        {
            setSubmitting(false);
        }
    };

    useEffect(() =>
    {
        if (sessionDocument?.id)
        {
            fetchComments();
        }
    }, [sessionDocument?.id]);

    return (
        <div className={styles.commentsContainer}>
            <div className={styles.commentsList}>
                {loading ? (
                    <Spinner/>
                ) : comments.length > 0 ? (
                    comments.map((comment) => (
                        <SessionDocumentComment key={comment.id} comment={comment}/>
                    ))
                ) : (
                    <div className={styles.noComments}>No comments yet</div>
                )}
            </div>

            <div className={styles.commentFieldContainer}>
                <Field className={styles.commentField}>
                    <Textarea
                        placeholder="Add a comment"
                        maxLength={255}
                        value={newComment}
                        onChange={(e, data) => setNewComment(data.value)}
                        disabled={submitting}
                    />
                </Field>
                <Button
                    icon={<SendCommentIcon/>}
                    appearance="transparent"
                    onClick={handleAddComment}
                    disabled={!newComment.trim() || submitting}
                />
            </div>
        </div>
    );
};

export default SessionDocumentComments;