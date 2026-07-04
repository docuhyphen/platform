import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "1.25rem",
    },

    // Keeps the back button row and the Form/Preview tabs visible while the
    // rest of the designer scrolls underneath. `top: 0` (not the settings
    // header height) matches the Document Library tab's sticky pattern: the
    // scrolling ancestor already reserves space for the fixed app header via
    // its own padding, so this only needs to stick once it reaches the top of
    // that scroll area, instead of double-offsetting and overlapping content
    // scrolled beneath it.
    stickyToolbar: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
        paddingBottom: "0.5rem",
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
