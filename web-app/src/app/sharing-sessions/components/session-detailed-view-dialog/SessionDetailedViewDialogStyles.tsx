import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionDDetailedViewDialogStyles = makeStyles({
    dialogSurface: {
        width: "min(760px, 92vw)",
        maxHeight: "85vh",
    },

    dialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        maxHeight: "68vh",
        overflowY: "auto",
        paddingRight: "4px",
    },

    sectionCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "8px",
        padding: "12px",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },

    titleRow: {
        display: "grid",
        gridTemplateColumns: "1fr auto",
        alignItems: "start",
        gap: "8px",
        minWidth: 0,
    },

    sessionTitleText: {
        minWidth: 0,
        whiteSpace: "normal",
        overflowWrap: "anywhere",
        wordBreak: "break-word",
    },

    statusChip: {
        whiteSpace: "nowrap",
        alignSelf: "start",
    },

    keyValueGrid: {
        display: "grid",
        gridTemplateColumns: "minmax(180px, 240px) 1fr",
        columnGap: "12px",
        rowGap: "6px",
    },

    keyLabel: {
        color: tokens.colorNeutralForeground3,
    },
});