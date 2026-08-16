import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationTrialActionsStyles = makeStyles({
    actions: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "flex-end",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
});
