import {DocumentCommentDetailedDto} from "../app/models/models.tsx";
import {addDocumentComment, getDocumentComments} from "./sharingSessionApi.ts";

export class DocumentCommentService
{
    async getComments(sessionId: string, documentId: string): Promise<DocumentCommentDetailedDto[]>
    {
        try
        {
            const response = await getDocumentComments(sessionId, documentId);
            return response as DocumentCommentDetailedDto[];
        }
        catch (error)
        {
            console.error('Failed to fetch comments:', error);
            throw new Error('Failed to fetch comments');
        }
    }

    async addComment(
        sessionId: string,
        documentId: string,
        commentText: string,
        commentedBy: string
    ): Promise<DocumentCommentDetailedDto>
    {
        try
        {
            const response = await addDocumentComment(
                sessionId,
                documentId,
                commentText,
                commentedBy
            );
            return response as DocumentCommentDetailedDto;
        }
        catch (error)
        {
            console.error('Failed to add comment:', error);
            throw new Error('Failed to add comment');
        }
    }
}