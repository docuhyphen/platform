import {Button, CounterBadge, Tooltip} from "@fluentui/react-components";
import {CommentIcon} from "../../../../components/IconBundles.tsx";
import {usePreviewNotesToggleStyles} from "./PreviewNotesToggleStyles.tsx";

interface PreviewNotesToggleProps
{
    isNotesPanelOpen: boolean;
    unreadCommentCount: number;
    onToggle: () => void;
}

const PreviewNotesToggle = ({isNotesPanelOpen, unreadCommentCount, onToggle}: PreviewNotesToggleProps) =>
{
    const styles = usePreviewNotesToggleStyles();
    const label = isNotesPanelOpen ? "Hide notes and comments" : "Show notes and comments";

    return (
        <div className={styles.notesControl}
             id={"exchange-document-preview-notes-control"}>
            <Tooltip content={label}
                     relationship="description">
                <Button onClick={onToggle}
                        id="exchange-document-preview-notes-toggle"
                        aria-label={label}
                        aria-pressed={isNotesPanelOpen}
                        appearance={isNotesPanelOpen ? "primary" : "transparent"}
                        shape={"circular"}
                        icon={<CommentIcon/>}
                />
            </Tooltip>
            {unreadCommentCount > 0 && (
                <CounterBadge id={"exchange-document-preview-notes-unread-count"}
                              className={styles.notesUnreadBadge}
                              count={unreadCommentCount}
                              size={"small"}
                              appearance={"filled"}
                              color={"danger"}
                />
            )}
        </div>
    );
};

export default PreviewNotesToggle;
