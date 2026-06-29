import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useLinkedProviderCardStyles = makeStyles({
    card: {
        ...shorthands.padding("16px"),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        boxShadow: tokens.shadow4,
    },

    contentRow: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "16px",
        flexWrap: "wrap",
    },

    identityBlock: {
        display: "flex",
        alignItems: "center",
        gap: "14px",
        flex: "1 1 360px",
        minWidth: 0,
    },

    providerMark: {
        width: "42px",
        height: "42px",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        ...shorthands.borderRadius(tokens.borderRadiusCircular),
        backgroundColor: tokens.colorBrandBackground2,
        color: tokens.colorBrandForeground1,
        flexShrink: 0,
    },

    metaBlock: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
        minWidth: 0,
    },

    titleRow: {
        display: "flex",
        alignItems: "center",
        gap: "10px",
        flexWrap: "wrap",
    },

    description: {
        color: tokens.colorNeutralForeground3,
    },

    actionBlock: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-end",
        gap: "8px",
        minWidth: "180px",
        "@media (max-width: 640px)": {
            width: "100%",
            alignItems: "stretch",
            minWidth: 0,
        },
    },

    helperText: {
        color: tokens.colorNeutralForeground3,
        textAlign: "right",
        "@media (max-width: 640px)": {
            textAlign: "left",
        },
    },
});
