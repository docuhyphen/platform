import {makeStyles, tokens} from "@fluentui/react-components";

export const useLinkedProviderListStyles = makeStyles({
    providerList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
});
