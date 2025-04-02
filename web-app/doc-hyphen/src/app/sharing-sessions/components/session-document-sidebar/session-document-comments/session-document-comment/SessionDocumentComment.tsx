import React from "react";
import {Avatar, Caption1, Card, CardHeader, Text} from "@fluentui/react-components";
import {useSessionDocumentCommentStyles} from "./SessionDocumentCommentStyles.tsx";
import {DocumentCommentDetailedDto} from "../../../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../../../helpers.ts";

interface SessionDocumentCommentProps
{
    comment: DocumentCommentDetailedDto;
}

const SessionDocumentComment: React.FC<SessionDocumentCommentProps> = ({comment}) =>
{
    const styles = useSessionDocumentCommentStyles();

    return (
        <Card className={styles.container} id={"styles.container"}>
            <CardHeader
                image={
                    <Avatar name={`${comment.commentedByFirstName} ${comment.commentedByLastName}`}/>
                }
                header={
                    <Text weight={"medium"}>
                        {`${comment.commentedByFirstName} ${comment.commentedByLastName}`}
                    </Text>
                }
                description={<Caption1>{formatDateTimeWithOrdinal(comment.createdDate)}</Caption1>}
            />
            <Text className={styles.commentText}>{comment.text}</Text>
        </Card>
    );
};

export default SessionDocumentComment;