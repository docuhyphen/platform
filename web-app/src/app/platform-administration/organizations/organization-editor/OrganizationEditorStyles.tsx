import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationEditorStyles = makeStyles({
    dialogSurface: {
        width: "min(680px, calc(100vw - 32px))",
        maxWidth: "calc(100vw - 32px)",
        maxHeight: "calc(100vh - 32px)",
        boxSizing: "border-box",
    },
    dialogBody: {
        minWidth: 0,
        minHeight: 0,
        maxHeight: "100%",
    },
    content: {
        display: "flex",
        flexDirection: "column",
        minWidth: 0,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        ...shorthands.gap(tokens.spacingVerticalM),
        ...shorthands.padding(tokens.spacingVerticalXS, tokens.spacingHorizontalXS),
    },
    policyGrid: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)",
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
    seatUsage: {
        display: "flex",
        flexWrap: "wrap",
        ...shorthands.gap(tokens.spacingHorizontalL),
    },
    entitlements: {
        display: "flex",
        flexDirection: "column",
        minWidth: 0,
        ...shorthands.gap(tokens.spacingVerticalS),
    },
    featureDropdown: {
        width: "100%",
        minWidth: 0,
    },
    entitlementRow: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) auto auto",
        alignItems: "center",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
    entitlementName: {
        minWidth: 0,
        overflowWrap: "anywhere",
    },
    footer: {
        display: "flex",
        justifyContent: "flex-end",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
});
