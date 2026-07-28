import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationsStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        ...shorthands.gap(tokens.spacingVerticalM),
    },
    loading: {
        display: "flex",
        justifyContent: "center",
        ...shorthands.padding(tokens.spacingVerticalXL),
    },
    pagination: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        flexWrap: "wrap",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
});
