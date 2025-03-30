import React from "react";
import {Caption1, Text} from "@fluentui/react-components";
import {useSessionDocumentCommentStyles} from "./SessionDocumentCommentStyles.tsx";
import {DocumentCommentDetailedDto} from "../../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../../helpers.ts";

interface SessionDocumentCommentProps
{
    comment: DocumentCommentDetailedDto;
}

const SessionDocumentComment: React.FC<SessionDocumentCommentProps> = ({comment}) =>
{
    const styles = useSessionDocumentCommentStyles();

    return (
        <div className={styles.commentContainer}>
            <div className={styles.commentHeader}>
                <Text weight="semibold">{`${comment.commentedByFirstName} ${comment.commentedByLastName}`}</Text>
                <Text weight="semibold">{comment.commentedByEmail}</Text>
                <Caption1>{formatDateTimeWithOrdinal(comment.createdDate)}</Caption1>
            </div>
            <Text className={styles.commentText}>{comment.text}</Text>
        </div>
    );
};

export default SessionDocumentComment;