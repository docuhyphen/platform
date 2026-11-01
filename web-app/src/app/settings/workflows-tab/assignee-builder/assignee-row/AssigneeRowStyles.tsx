import {makeStyles, tokens} from "@fluentui/react-components";
import {assigneeFlexFieldStyle} from "../assigneeBuilderStyleFragments.ts";

export const useAssigneeRowStyles = makeStyles({
    row: {
        display: "flex",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalS,
        padding: tokens.spacingHorizontalS,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        minWidth: 0,
        "@media (max-width: 900px)": {
            flexWrap: "wrap",
        },
    },

    rowFields: {
        flex: 1,
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    kindSelect: assigneeFlexFieldStyle,

    removeButton: {
        flexShrink: 0,
        "@media (max-width: 900px)": {
            marginLeft: "auto",
        },
    },
});
