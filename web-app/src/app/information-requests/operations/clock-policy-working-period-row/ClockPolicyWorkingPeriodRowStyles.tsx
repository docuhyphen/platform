import {makeStyles, tokens} from "@fluentui/react-components";

export const useClockPolicyWorkingPeriodRowStyles = makeStyles({
    item: {
        paddingBottom: tokens.spacingVerticalS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    row: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1.4fr) minmax(0, 1fr) minmax(0, 1fr) auto",
        alignItems: "start",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalS,
        "@media (max-width: 640px)": {
            gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        },
    },
    day: {
        "@media (max-width: 640px)": {
            gridColumn: "1 / -1",
        },
    },
    end: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
    },
    remove: {
        alignSelf: "center",
        "@media (max-width: 640px)": {
            gridColumn: "1 / -1",
            justifySelf: "end",
        },
    },
});
