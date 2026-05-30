import {
    BrandVariants,
    createDarkTheme,
    createLightTheme,
    Theme,
} from "@fluentui/react-components";

export type ThemeMode = "light" | "dark" | "system";

export const mainBrand: BrandVariants = {
    10: "#030204",
    20: "#17161E",
    30: "#232433",
    40: "#2D2F46",
    50: "#363C59",
    60: "#3E496C",
    70: "#455681",
    80: "#4B6496",
    90: "#5073AB",
    100: "#5482C1",
    110: "#5691D7",
    120: "#5EA1E7",
    130: "#77AFEB",
    140: "#8FBEEE",
    150: "#A7CDF2",
    160: "#BFDBF5",
};

export const lightTheme: Theme = {
    ...createLightTheme(mainBrand),
};

export const darkTheme: Theme = {
    ...createDarkTheme(mainBrand),
};

darkTheme.colorBrandForeground1 = mainBrand[110];
darkTheme.colorBrandForeground2 = mainBrand[120];

