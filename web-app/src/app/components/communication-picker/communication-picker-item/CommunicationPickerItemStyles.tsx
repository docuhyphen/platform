import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationPickerItemStyles = makeStyles({
    itemCard: {
        border: `${tokens.strokeWidthThick} solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalM}`,
        cursor: "pointer",
        background: tokens.colorTransparentBackground,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },
    itemCardSelected: {
        borderTopColor: tokens.colorBrandStroke1,
        borderRightColor: tokens.colorBrandStroke1,
        borderBottomColor: tokens.colorBrandStroke1,
        borderLeftColor: tokens.colorBrandStroke1,
        background: tokens.colorBrandBackground2,
    },
    itemHeader: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    itemSubject: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },
    itemSummary: {
        color: tokens.colorNeutralForeground2,
    },
});
