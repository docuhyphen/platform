import {makeStyles, tokens} from "@fluentui/react-components";

export const useBlueprintTableRowStyles = makeStyles({
    tr: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ":hover": {backgroundColor: tokens.colorNeutralBackground2},
    },
    td: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        verticalAlign: "middle",
    },
});
