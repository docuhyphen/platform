import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: 0,
        height: "100%",
        minHeight: 0,
    },

    tabListWrapper: {
        background: tokens.colorNeutralBackground1,
        paddingBottom: "4px",
        boxSizing: "border-box",
        minHeight: "2.75rem",
        flexShrink: 0,
        paddingInline: "0.5rem",
        boxSizing: "border-box",
    },

    content: {
        flex: 1,
        minHeight: 0,
        overflow: "hidden",
    },
    scrollableContent: {
        height: "100%",
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: "0.5rem",
        boxSizing: "border-box",
    },
});

