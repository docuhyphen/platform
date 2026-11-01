import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationsFilterPopoverStyles = makeStyles({
    filterPopover: {
        padding: tokens.spacingHorizontalS,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        minWidth: "220px",
        maxWidth: "280px",
    },
    filterSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },
    filterSectionTitle: {
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
    },
    filterList: {
        minHeight: "32px",
        maxHeight: "180px",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        padding: `${tokens.spacingVerticalXS} 0`,
    },
});
