import {makeStyles, tokens} from "@fluentui/react-components";

export const usePartyRowStyles = makeStyles({
    row: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) auto",
        alignItems: "center",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalXS,
        paddingTop: tokens.spacingVerticalS,
        paddingBottom: tokens.spacingVerticalS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        "@media (max-width: 640px)": {
            gridTemplateColumns: "minmax(0, 1fr)",
        },
    },
    identity: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
    },
    label: {
        overflowWrap: "anywhere",
    },
    meta: {
        color: tokens.colorNeutralForeground2,
    },
    trustBadge: {
        alignSelf: "flex-start",
    },
    actions: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
    },
});
