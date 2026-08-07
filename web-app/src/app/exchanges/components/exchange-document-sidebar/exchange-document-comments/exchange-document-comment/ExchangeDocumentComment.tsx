import React from "react";
import {Avatar, Badge, Button, Popover, PopoverSurface, PopoverTrigger, Text, Tooltip} from "@fluentui/react-components";
import {useExchangeDocumentCommentStyles} from "./ExchangeDocumentCommentStyles.tsx";
import {DocumentCommentDetailedDto} from "../../../../../models/models.tsx";
import {useAvatarUrl} from "../../../../../components/hooks/useAvatarUrl.ts";
import {formatDateTimeWithOrdinal} from "../../../../../helpers.ts";

interface ExchangeDocumentCommentProps
{
    comment: DocumentCommentDetailedDto;
    idPrefix?: string;
    onNavigateToPage?: (pageNumber: number) => void;
}

const ExchangeDocumentComment: React.FC<ExchangeDocumentCommentProps> = (
    {
        comment,
        idPrefix = "document",
        onNavigateToPage,
    }) =>
{
    const styles = useExchangeDocumentCommentStyles();
    const authorAvatarUrl = useAvatarUrl(comment.commentedByAvatarUrl);

    return (
        <div
            className={styles.container}
            id={`${idPrefix}-comment-${comment.id}`}
        >
            <Popover withArrow openOnHover>
                <PopoverTrigger disableButtonEnhancement>
                    <Avatar
                        id={`${idPrefix}-comment-${comment.id}-author-avatar`}
                        name={`${comment.commentedByFirstName} ${comment.commentedByLastName}`}
                        image={authorAvatarUrl ? {src: authorAvatarUrl} : undefined}
                    />
                </PopoverTrigger>

                <PopoverSurface>
                    <div id={`${idPrefix}-comment-${comment.id}-author-details`}>
                        <Text
                            id={`${idPrefix}-comment-${comment.id}-author-name`}
                            weight={"medium"}
                        >
                            {`${comment.commentedByFirstName} ${comment.commentedByLastName}`}
                        </Text>
                    </div>
                </PopoverSurface>
            </Popover>
            <div className={styles.commentTextContainer}>
                <div
                    id={`${idPrefix}-comment-${comment.id}-metadata`}
                    className={styles.commentMetadata}
                >
                    {comment.isInternal && (
                        <Badge
                            id={`${idPrefix}-comment-${comment.id}-internal-badge`}
                            appearance={"tint"}
                            color={"brand"}
                            size={"small"}
                        >
                            Internal
                        </Badge>
                    )}
                    {comment.documentVersion && (
                        <Badge
                            id={`${idPrefix}-comment-${comment.id}-version-badge`}
                            appearance={"outline"}
                            size={"small"}
                        >
                            Version {comment.documentVersion}
                        </Badge>
                    )}
                    {comment.pageNumber && (
                        <Tooltip
                            content={`Go to page ${comment.pageNumber}`}
                            relationship={"description"}
                        >
                            <Button
                                id={`${idPrefix}-comment-${comment.id}-page`}
                                appearance={"outline"}
                                shape={"circular"}
                                size={"small"}
                                disabled={!onNavigateToPage}
                                onClick={() => onNavigateToPage?.(comment.pageNumber!)}
                            >
                                P/{comment.pageNumber}
                            </Button>
                        </Tooltip>
                    )}
                    <Text
                        id={`${idPrefix}-comment-${comment.id}-date`}
                        className={styles.commentDate}
                        size={100}
                        weight={"semibold"}
                    >
                        {formatDateTimeWithOrdinal(comment.createdDate)}
                    </Text>
                </div>
                <Text
                    id={`${idPrefix}-comment-${comment.id}-text`}
                    className={styles.commentText}
                >
                    {comment.text}
                </Text>
            </div>
        </div>
    );
};

export default ExchangeDocumentComment;
