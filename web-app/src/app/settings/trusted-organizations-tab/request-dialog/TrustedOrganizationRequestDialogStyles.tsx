import {makeStyles, tokens} from "@fluentui/react-components";

export const useTrustedOrganizationRequestDialogStyles = makeStyles({
    body: {
        display: "grid",
        gap: tokens.spacingVerticalM,
        minWidth: "min(32rem, 80vw)",
        "@media (max-width: 520px)": {
            minWidth: 0,
        },
    },
    search: {
        display: "grid",
        gridTemplateColumns: "1fr auto",
        gap: tokens.spacingHorizontalS,
        "@media (max-width: 420px)": {
            gridTemplateColumns: "1fr",
        },
    },
});
