import {makeStyles} from "@fluentui/react-components";

export const useExchangeInitiationTabPanelsStyles = makeStyles({
    exchangeInitiationTaps: {
        minHeight: "400px",
        "@media (max-width: 768px)": {
            minHeight: "auto",
            width: "100%",
            minWidth: 0,
        },
    },
});
