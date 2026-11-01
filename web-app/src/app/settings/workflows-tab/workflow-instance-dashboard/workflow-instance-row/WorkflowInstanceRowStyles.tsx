import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowInstanceRowStyles = makeStyles({
    row: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        padding: `${tokens.spacingVerticalM} 0`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        cursor: "pointer",
        ":hover": {
            background: tokens.colorNeutralBackground2,
        },
    },
    rowInfo: {
        flex: 1,
        minWidth: 0,
    },
    rowMetaInherit: {
        color: "inherit",
    },
});
