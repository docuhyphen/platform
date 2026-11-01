import {makeStyles, tokens} from "@fluentui/react-components";

export const useBlueprintsTagFilterStyles = makeStyles({
    filterPopover: {
        padding: tokens.spacingHorizontalS,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        minWidth: "180px",
        maxWidth: "260px",
    },
    filterPopoverList: {
        minHeight: "80px",
        maxHeight: "220px",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
    },
    filterEmptyText: {
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        color: tokens.colorNeutralForeground3,
    },
});
