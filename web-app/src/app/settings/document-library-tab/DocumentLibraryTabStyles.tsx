import {makeStyles, tokens} from "@fluentui/react-components";

export const useDocumentsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "100%",
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: "8px",
    },
    card: {
        minWidth: "0",
        border: "1px solid var(--colorNeutralStroke1)",
        borderRadius: tokens.borderRadiusXLarge,
        padding: "12px 16px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: "8px",
    },
    cardBody: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: "1",
        minWidth: "0",
    },
    badgeRow: {
        display: "flex",
        gap: "6px",
        flexWrap: "wrap",
        alignItems: "center",
    },
    cardActions: {
        display: "flex",
        gap: "4px",
        alignItems: "center",
        flexShrink: "0",
    },
    dialogBody: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        paddingTop: "8px",
    },
    dialogActions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: "8px",
        marginTop: "8px",
    },
    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "12px",
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
    tagInput: {
        display: "flex",
        flexWrap: "wrap",
        gap: "4px",
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: "4px 8px",
        minHeight: "32px",
        cursor: "text",
    },
});
