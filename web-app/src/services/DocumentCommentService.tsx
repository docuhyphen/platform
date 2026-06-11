import {DocumentCommentDetailedDto} from "../app/models/models.tsx";
import {addDocumentComment, getDocumentComments} from "./exchangeApi.ts";

export class DocumentCommentService
{
    async getComments(exchangeId: string, documentId: string): Promise<DocumentCommentDetailedDto[]>
    {
        try
        {
            const response = await getDocumentComments(exchangeId, documentId);
            return response as DocumentCommentDetailedDto[];
        }
        catch (error)
        {
            console.error('Failed to fetch comments:', error);
            throw new Error('Failed to fetch comments');
        }
    }

    async addComment(
        exchangeId: string,
        documentId: string,
        commentText: string,
        commentedBy: string
    ): Promise<DocumentCommentDetailedDto>
    {
        try
        {
            const response = await addDocumentComment(
                exchangeId,
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