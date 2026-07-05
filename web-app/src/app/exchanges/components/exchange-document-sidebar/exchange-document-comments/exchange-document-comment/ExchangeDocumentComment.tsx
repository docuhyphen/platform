import React from "react";
import {Avatar, Badge, Popover, PopoverSurface, PopoverTrigger, Text} from "@fluentui/react-components";
import {useExchangeDocumentCommentStyles} from "./ExchangeDocumentCommentStyles.tsx";
import {DocumentCommentDetailedDto} from "../../../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../../../helpers.ts";

interface ExchangeDocumentCommentProps
{
    comment: DocumentCommentDetailedDto;
}

const ExchangeDocumentComment: React.FC<ExchangeDocumentCommentProps> = ({comment}) =>
{
    const styles = useExchangeDocumentCommentStyles();

    return (
        <div
            className={styles.container}
            id={`document-comment-${comment.id}`}
        >
            <Popover withArrow openOnHover>
                <PopoverTrigger disableButtonEnhancement>
                    <Avatar name={`${comment.commentedByFirstName} ${comment.commentedByLastName}`}/>
                </PopoverTrigger>

                <PopoverSurface>
                    <div>
                        <Text weight={"medium"}>
                            {`${comment.commentedByFirstName} ${comment.commentedByLastName}`}
                        </Text>
                    </div>
                </PopoverSurface>
            </Popover>
            <div className={styles.commentTextContainer}>
                <div
                    id={`document-comment-${comment.id}-metadata`}
                    className={styles.commentMetadata}
                >
                    {comment.isInternal && (
                        <Badge
                            id={`document-comment-${comment.id}-internal-badge`}
                            appearance={"tint"}
                            color={"brand"}
                            size={"small"}
                        >
                            Internal
                        </Badge>
                    )}
                    <Text
                        id={`document-comment-${comment.id}-date`}
                        className={styles.commentDate}
                        size={100}
                        weight={"semibold"}
                    >
                        {formatDateTimeWithOrdinal(comment.createdDate)}
                    </Text>
                </div>
                <Text className={styles.commentText}>{comment.text}</Text>
            </div>
        </div>
    );
};

export default ExchangeDocumentComment;
