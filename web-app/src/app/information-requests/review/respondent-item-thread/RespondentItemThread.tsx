import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {InformationRequestReviewCommentDto, InformationRequestReviewResultItemDto} from "../../../models/models.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {commentAuthorLabel} from "../reviewLabels.ts";
import {useRespondentItemThreadStyles} from "./RespondentItemThreadStyles.tsx";

interface Props {
    idPrefix: string;
    item: InformationRequestReviewResultItemDto;
    label: string;
    comments: InformationRequestReviewCommentDto[];
    canComment: boolean;
    busy: boolean;
    onComment: (item: InformationRequestReviewResultItemDto, body: string) => Promise<boolean>;
}

const RespondentItemThread = ({idPrefix, item, label, comments, canComment, busy, onComment}: Props) => {
    const styles = useRespondentItemThreadStyles();
    const [writing, setWriting] = useState(false);
    const [body, setBody] = useState("");
    const id = `${idPrefix}-item-${item.submissionItemId}`;

    const send = async () => {
        if (await onComment(item, body.trim())) {
            setBody("");
            setWriting(false);
        }
    };

    return (
        <li id={id}
            className={styles.thread}>
            <Text id={`${id}-requirement`}
                  weight={"semibold"}>
                {label}
            </Text>
            {comments.length > 0 && (
                <ul id={`${id}-conversation`}
                    className={styles.conversation}>
                    {comments.map(comment => (
                        <li key={comment.id}
                            id={`${id}-comment-${comment.id}`}
                            className={styles.comment}>
                            {`${commentAuthorLabel(comment)}: ${comment.body}`}
                        </li>
                    ))}
                </ul>
            )}
            {canComment && !writing && (
                <Button id={`${id}-comment`}
                        size={"small"}
                        shape={"circular"}
                        appearance={"subtle"}
                        aria-label={`Comment on ${label}`}
                        onClick={() => setWriting(true)}>
                    Comment
                </Button>
            )}
            {writing && (
                <div id={`${id}-comment-form`}
                     className={styles.form}>
                    <TextField id={`${id}-comment-body`}
                               label={"Your comment"}
                               multiline={true}
                               maxLength={4000}
                               value={body}
                               onChange={setBody}/>
                    <div id={`${id}-comment-actions`}
                         className={styles.actions}>
                        <Button id={`${id}-comment-send`}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || !body.trim()}
                                onClick={() => { void send(); }}>
                            Send comment
                        </Button>
                        <Button id={`${id}-comment-cancel`}
                                appearance={"secondary"}
                                shape={"circular"}
                                disabled={busy}
                                onClick={() => setWriting(false)}>
                            Cancel
                        </Button>
                    </div>
                </div>
            )}
        </li>
    );
};

export default RespondentItemThread;
