import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: 0,
        height: "100%",
        minHeight: 0,
    },

    // Keeps the back button row and the Form/Preview tabs visible while the
    // rest of the designer scrolls underneath. `top: 0` (not the settings
    // header height) matches the Document Library tab's sticky pattern: the
    // scrolling ancestor already reserves space for the fixed app header via
    // its own padding, so this only needs to stick once it reaches the top of
    // that scroll area, instead of double-offsetting and overlapping content
    // scrolled beneath it.
    stickyToolbar: {
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
        paddingBottom: "0.5rem",
        flexShrink: 0,
        paddingInline: "0.5rem",
        boxSizing: "border-box",
    },

    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: "0.5rem",
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: "1.25rem",
    },

    applicabilitySection: {
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
    },

    previewLoading: {
        display: "flex",
        justifyContent: "center",
        padding: "2rem",
    },
});
