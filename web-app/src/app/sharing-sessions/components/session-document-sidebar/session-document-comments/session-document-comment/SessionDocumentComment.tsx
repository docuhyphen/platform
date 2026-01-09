import React from "react";
import {Avatar, Popover, PopoverSurface, PopoverTrigger, Text} from "@fluentui/react-components";
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
        <div className={styles.container} id={"styles.container"}>
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
                <Text className={styles.commentDate}
                      size={100}
                      weight={"semibold"}>
                    {formatDateTimeWithOrdinal(comment.createdDate)}
                </Text>
                <Text className={styles.commentText}>{comment.text}</Text>
            </div>
        </div>
    );
};

export default SessionDocumentComment;