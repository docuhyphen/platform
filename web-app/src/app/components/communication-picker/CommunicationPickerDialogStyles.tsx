import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationPickerDialogStyles = makeStyles({
    dialogSurface: {
        maxWidth: "600px",
        width: "100%",
    },
    contentContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    itemList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        maxHeight: "360px",
        overflowY: "auto",
    },
    itemCard: {
        border: `2px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalM}`,
        cursor: "pointer",
        background: "transparent",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },
    itemCardSelected: {
        borderTopColor: tokens.colorBrandStroke1,
        borderRightColor: tokens.colorBrandStroke1,
        borderBottomColor: tokens.colorBrandStroke1,
        borderLeftColor: tokens.colorBrandStroke1,
        background: tokens.colorBrandBackground2,
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
    itemHeader: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    noResults: {
        color: tokens.colorNeutralForeground3,
    },
});
