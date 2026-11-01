import {makeStyles} from '@fluentui/react-components';

export const usePreviewNotesToggleStyles = makeStyles({
    notesControl: {
        position: "relative",
        display: "inline-flex",
    },

    notesUnreadBadge: {
        position: "absolute",
        top: 0,
        right: 0,
        transform: "translate(35%, -35%)",
        pointerEvents: "none",
    },
});
