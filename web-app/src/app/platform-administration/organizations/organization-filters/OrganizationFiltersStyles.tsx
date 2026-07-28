import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationFiltersStyles = makeStyles({
    form: {
        display: "grid",
        gridTemplateColumns: "minmax(180px, 2fr) minmax(140px, 1fr) minmax(140px, 1fr) auto",
        alignItems: "end",
        ...shorthands.gap(tokens.spacingHorizontalM),
        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
    action: {
        justifySelf: "end",
    },
});
