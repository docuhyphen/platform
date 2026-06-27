import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "100%",
        minWidth: 0,
        height: "100%"
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: "8px",
    },
    sessionCard: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "flex-start",
        padding: "12px 16px",
        borderRadius: tokens.borderRadiusXLarge,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        gap: "16px",
        // Stack the meta block and the action row on phones; the inline
        // layout pushes the destructive button off-screen on narrow widths.
        "@media (max-width: 600px)": {
            flexDirection: "column",
            alignItems: "stretch",
            gap: "8px",
            padding: "12px",
        },
    },
    sessionCardContainer: {
        display: "flex",
        flex: "1",
        flexDirection: "column",
        overflowY: "auto",
        minHeight: 0,
        gap: "8px"
    },
    sessionMeta: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: 1,
        minWidth: 0,
        wordBreak: "break-word",
    },
    sessionActions: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
    },
});
