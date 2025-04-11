import React, {useEffect, useRef, useState} from "react";
import {
    Button,
    Divider,
    Field,
    Spinner,
    Text,
    Textarea,
    Toast,
    ToastTitle,
    useId,
    useToastController
} from "@fluentui/react-components";
import SessionDocumentComment from "./session-document-comment/SessionDocumentComment";
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
    const [addingComment, setAddingComment] = useState<boolean>(false);
    const commentService = new DocumentCommentService();
    const styles = useSessionDocumentCommentsStyles();
    const toasterId = useId("document-comments-toaster");
    const {dispatchToast} = useToastController(toasterId);
    const commentsListRef = useRef<HTMLDivElement>(null);

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

    const onAddComment = async () =>
    {
        if (addingComment)
        {
            return;
        }

        setAddingComment(true);

        if (!newComment.trim())
        {
            return;
        }

        try
        {
            const addedComment = await commentService.addComment(
                sessionId,
                sessionDocument.id,
                newComment,
                currentUserEmail
            );

            setComments([addedComment, ...comments]);
            setNewComment("");

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
        if (sessionDocument?.id)
        {
            fetchComments();
        }
    }, [sessionDocument?.id]);

    const showServerErrorToast = (message: string) =>
    {
        dispatchToast(
            <Toast>
                <ToastTitle> {message}</ToastTitle>
            </Toast>, {intent: 'error', timeout: 15000, position: "top"},
        );
    };

    const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) =>
    {
        if (event.key === 'Enter' && !event.shiftKey)
        {
            event.preventDefault();
            onAddComment();
        }
    };

    return (
        <div className={styles.container}>
            <div className={styles.list} ref={commentsListRef}>
                {loading ? (
                    <Spinner size={"small"}/>
                ) : comments.length > 0 ? (
                    comments.map((comment, index) => (

                        <SessionDocumentComment comment={comment}/>
                    ))
                ) : (
                    <div className={styles.noComments}>No notes have been added yet.</div>
                )}
            </div>

            <div className={styles.commentFieldContainer}>
                <div className={styles.commentFieldContainerField}>
                    <Field className={styles.commentField}>
                        <Textarea
                            placeholder="Add note"
                            maxLength={255}
                            value={newComment}
                            onChange={(e, data) => setNewComment(data.value)}
                            onKeyDown={handleKeyDown}
                            disabled={addingComment}
                        />
                    </Field>
                </div>
                <div className={styles.commentCounterSend}>
                    <Text>
                        {newComment.length}/255
                    </Text>

                    <Button
                        icon={addingComment ? <Spinner size={"extra-small"}/> : <SendCommentIcon/>}
                        appearance="transparent"
                        onClick={onAddComment}
                        size={"large"}
                        disabled={!newComment.trim() || addingComment}
                    />
                </div>
            </div>
        </div>
    );
};

export default SessionDocumentComments;