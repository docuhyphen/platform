import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateEditorFooterStyles = makeStyles({
    footer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        paddingTop: tokens.spacingVerticalS,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        flexShrink: 0,
    },
    actions: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    state: {
        color: tokens.colorNeutralForeground3,
        marginInlineEnd: "auto",
    },
});
