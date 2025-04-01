import React, {useEffect, useState} from "react";
import {
    Button,
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

            setComments([...comments, addedComment]);
            setNewComment("");
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

    return (
        <div className={styles.container}>
            <div className={styles.list}>
                {loading ? (
                    <Spinner/>
                ) : comments.length > 0 ? (
                    comments.map((comment) => (
                        <SessionDocumentComment key={comment.id} comment={comment}/>
                    ))
                ) : (
                    <div className={styles.noComments}>No notes have been added yet.</div>
                )}
            </div>

            <div className={styles.commentFieldContainer}>
                <div className={styles.commentFieldContainerField}>
                    <Field className={styles.commentField}>
                        <Textarea
                            placeholder="Add a note"
                            maxLength={255}
                            value={newComment}
                            onChange={(e, data) => setNewComment(data.value)}
                            disabled={addingComment}
                        />
                    </Field>
                    <Button
                        icon={<SendCommentIcon/>}
                        appearance="transparent"
                        onClick={onAddComment}
                        disabled={!newComment.trim() || addingComment}
                    />
                </div>
                <div className={styles.commentCounter}>
                    <Text>
                        {newComment.length}/255
                    </Text>
                    {addingComment && <Spinner size={"extra-small"}/>}
                </div>
            </div>
        </div>
    );
};

export default SessionDocumentComments;