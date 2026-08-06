import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import {useNotifications} from "../../../../../context/NotificationContext.tsx";
import {DocumentCommentService} from "../../../../../services/DocumentCommentService.tsx";
import {realtimeService} from "../../../../../services/NotificationService.tsx";
import {DocumentCommentDetailedDto, DocumentDetailedDto} from "../../../../models/models.tsx";

interface UseExchangeDocumentCommentsOptions
{
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    pageNumber?: number;
    documentVersionId?: string;
}

const getDraftKey = (exchangeId: string, documentId: string, documentVersionId?: string) =>
    `exchanges.comments.draft.${exchangeId}.${documentId}.${documentVersionId ?? "latest"}`;

export const useExchangeDocumentComments = (
    {
        exchangeId,
        exchangeDocument,
        pageNumber,
        documentVersionId,
    }: UseExchangeDocumentCommentsOptions) =>
{
    const commentService = useMemo(() => new DocumentCommentService(), []);
    const commentsListRef = useRef<HTMLDivElement>(null);
    const {markMatchingAsRead} = useNotifications();
    const {currentSession, appUser} = useAuth();
    const currentUserId = appUser?.id;
    const [loading, setLoading] = useState(true);
    const [comments, setComments] = useState<DocumentCommentDetailedDto[]>([]);
    const [newComment, setNewComment] = useState("");
    const [isInternal, setIsInternal] = useState(false);
    const [linkToPage, setLinkToPage] = useState(true);
    const [addingComment, setAddingComment] = useState(false);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const documentId = exchangeDocument.id ?? "";
    const draftKey = getDraftKey(exchangeId, documentId, documentVersionId);
    const activeOrganization = currentSession?.availableOrganizations.find(
        organization => organization.organizationId === currentSession.activeOrganizationId
    );
    const canPostInternal = Boolean(activeOrganization);

    const fetchComments = useCallback(async (showLoading = true) =>
    {
        if (!documentId) return;
        try
        {
            if (showLoading) setLoading(true);
            const fetchedComments = await commentService.getComments(exchangeId, documentId);
            setComments(fetchedComments);
            setLoadError(null);
            markMatchingAsRead({
                eventTypes: ["document.commented"],
                data: {exchangeId, documentId},
            });
        }
        catch (error)
        {
            console.error("Failed to fetch comments:", error);
            setLoadError("Notes and comments could not be loaded.");
        }
        finally
        {
            if (showLoading) setLoading(false);
        }
    }, [commentService, documentId, exchangeId, markMatchingAsRead]);

    const addComment = useCallback(async () =>
    {
        const normalizedComment = newComment.trim();
        if (addingComment || !normalizedComment || !documentId) return;
        setAddingComment(true);
        setSubmitError(null);
        try
        {
            const addedComment = await commentService.addComment(
                exchangeId,
                documentId,
                normalizedComment,
                isInternal,
                linkToPage ? pageNumber : undefined,
                documentVersionId,
            );
            setComments(current => [addedComment, ...current.filter(comment => comment.id !== addedComment.id)]);
            setNewComment("");
            setIsInternal(false);
            setLinkToPage(true);
            window.localStorage.removeItem(draftKey);
            const commentsList = commentsListRef.current;
            if (commentsList && typeof commentsList.scrollTo === "function")
            {
                commentsList.scrollTo({top: 0, behavior: "smooth"});
            }
            else if (commentsList)
            {
                commentsList.scrollTop = 0;
            }
        }
        catch (error)
        {
            console.error("Failed to add note:", error);
            setSubmitError("Your note could not be posted.");
        }
        finally
        {
            setAddingComment(false);
        }
    }, [addingComment, commentService, documentId, documentVersionId, draftKey, exchangeId, isInternal, linkToPage, newComment, pageNumber]);

    useEffect(() =>
    {
        setNewComment(window.localStorage.getItem(draftKey) ?? "");
    }, [draftKey]);

    useEffect(() =>
    {
        if (newComment) window.localStorage.setItem(draftKey, newComment);
        else window.localStorage.removeItem(draftKey);
    }, [draftKey, newComment]);

    useEffect(() =>
    {
        if (!canPostInternal) setIsInternal(false);
    }, [canPostInternal]);

    useEffect(() =>
    {
        void fetchComments();
    }, [fetchComments]);

    useEffect(() => realtimeService.on("DOCUMENT_COMMENT_ADDED", message =>
    {
        if (message.userId && message.userId === currentUserId) return;
        if (message.exchangeId === exchangeId && message.documentId === documentId)
        {
            void fetchComments(false);
        }
    }), [currentUserId, documentId, exchangeId, fetchComments]);

    return {
        loading,
        comments,
        newComment,
        setNewComment,
        isInternal,
        setIsInternal,
        linkToPage,
        setLinkToPage,
        addingComment,
        loadError,
        submitError,
        commentsListRef,
        fetchComments,
        addComment,
        canPostInternal,
        internalOrganizationName: activeOrganization?.name,
    };
};
