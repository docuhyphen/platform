import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {
    InformationRequestReviewCommentDto,
    InformationRequestReviewCommentRole,
    InformationRequestReviewFindingDto,
} from "../../../models/models.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {findingSeverityLabels} from "../reviewLabels.ts";
import {useRespondentFindingStyles} from "./RespondentFindingStyles.tsx";

interface Props
{
    idPrefix: string;
    finding: InformationRequestReviewFindingDto;
    label: string;
    comments: InformationRequestReviewCommentDto[];
    canReply: boolean;
    busy: boolean;
    onReply: (finding: InformationRequestReviewFindingDto, body: string) => void;
}

const authorLabel = (comment: InformationRequestReviewCommentDto): string =>
{
    if (comment.authoredByCaller) return "You";
    if (comment.authorRole === InformationRequestReviewCommentRole.REVIEWER) return "Reviewer";
    if (comment.authorRole === InformationRequestReviewCommentRole.ADMINISTRATOR) return "Administrator";
    return "Respondent";
};

const RespondentFinding = ({idPrefix, finding, label, comments, canReply, busy, onReply}: Props) =>
{
    const styles = useRespondentFindingStyles();
    const [replying, setReplying] = useState(false);
    const [body, setBody] = useState("");
    const id = `${idPrefix}-finding-${finding.id}`;

    return (
        <li id={id}
            className={styles.finding}>
            <Text id={`${id}-requirement`}
                  weight={"semibold"}>
                {`${label} (${findingSeverityLabels[finding.severity].toLowerCase()})`}
            </Text>
            <Text id={`${id}-narrative`}>{finding.narrative}</Text>
            {comments.length > 0 && (
                <ul id={`${id}-conversation`}
                    className={styles.conversation}>
                    {comments.map(comment => (
                        <li key={comment.id}
                            id={`${id}-comment-${comment.id}`}
                            className={styles.comment}>
                            {`${authorLabel(comment)}: ${comment.body}`}
                        </li>
                    ))}
                </ul>
            )}
            {canReply && !replying && (
                <Button id={`${id}-reply`}
                        size={"small"}
                        shape={"circular"}
                        appearance={"subtle"}
                        aria-label={`Reply to the finding on ${label}`}
                        onClick={() => setReplying(true)}>
                    Reply
                </Button>
            )}
            {replying && (
                <div id={`${id}-reply-form`}
                     className={styles.reply}>
                    <TextField id={`${id}-reply-body`}
                               label={"Your reply"}
                               multiline={true}
                               maxLength={4000}
                               value={body}
                               onChange={setBody}/>
                    <div id={`${id}-reply-actions`}
                         className={styles.replyActions}>
                        <Button id={`${id}-reply-send`}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || !body.trim()}
                                onClick={() =>
                                {
                                    onReply(finding, body.trim());
                                    setReplying(false);
                                    setBody("");
                                }}>
                            Send reply
                        </Button>
                        <Button id={`${id}-reply-cancel`}
                                appearance={"secondary"}
                                shape={"circular"}
                                onClick={() => setReplying(false)}>
                            Cancel
                        </Button>
                    </div>
                </div>
            )}
        </li>
    );
};

export default RespondentFinding;
