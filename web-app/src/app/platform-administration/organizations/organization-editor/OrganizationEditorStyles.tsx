import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationEditorStyles = makeStyles({
    dialogBody: {
        width: "min(680px, calc(100vw - 32px))",
        maxHeight: "calc(100vh - 64px)",
    },
    content: {
        display: "flex",
        flexDirection: "column",
        overflowY: "auto",
        ...shorthands.gap(tokens.spacingVerticalM),
        ...shorthands.padding(tokens.spacingVerticalXS, tokens.spacingHorizontalXS),
    },
    policyGrid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        ...shorthands.gap(tokens.spacingHorizontalM),
        "@media (max-width: 600px)": {
            gridTemplateColumns: "1fr",
        },
    },
    statusOptions: {
        display: "flex",
        flexWrap: "wrap",
        ...shorthands.gap(tokens.spacingHorizontalL),
    },
    entitlements: {
        display: "flex",
        flexDirection: "column",
        ...shorthands.gap(tokens.spacingVerticalS),
    },
    entitlementRow: {
        display: "grid",
        gridTemplateColumns: "1fr auto auto",
        alignItems: "center",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
    footer: {
        display: "flex",
        justifyContent: "flex-end",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
});
