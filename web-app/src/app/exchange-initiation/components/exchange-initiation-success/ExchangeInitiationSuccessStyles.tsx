import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeInitiationSuccessStyles = makeStyles({
    exchangeInitiationSuccess: {
        minHeight: "200px",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },
    exchangeSuccessDetails: {
        display: "grid",
        gridTemplateColumns: "auto 1fr",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalS,
        width: "100%",
        maxWidth: "440px",
        padding: tokens.spacingHorizontalM,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    exchangeSuccessActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        alignItems: "center",
        marginBottom: tokens.spacingVerticalL
    },
});
