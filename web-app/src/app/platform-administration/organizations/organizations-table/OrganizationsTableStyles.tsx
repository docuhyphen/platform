import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationsTableStyles = makeStyles({
    scrollContainer: {
        width: "100%",
        overflowX: "auto",
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusMedium),
    },
    table: {
        minWidth: "900px",
    },
    actions: {
        textAlign: "right",
    },
    entitlements: {
        maxWidth: "260px",
        whiteSpace: "normal",
    },
});
