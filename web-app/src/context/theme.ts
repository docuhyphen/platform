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

/**
 * Lightens a hex color (#rrggbb) toward white by the given ratio (0..1).
 * Ratio 0.2 means each channel moves 20% of the distance to 255.
 */
function lighten(hex: string, ratio: number): string {
    const m = /^#?([0-9a-f]{6})$/i.exec(hex);
    if (!m) return hex;
    const n = parseInt(m[1], 16);
    const r = (n >> 16) & 0xff;
    const g = (n >> 8) & 0xff;
    const b = n & 0xff;
    const lr = Math.round(r + (255 - r) * ratio);
    const lg = Math.round(g + (255 - g) * ratio);
    const lb = Math.round(b + (255 - b) * ratio);
    return (
        "#" +
        [lr, lg, lb]
            .map((v) => v.toString(16).padStart(2, "0"))
            .join("")
    );
}

const baseDarkTheme = createDarkTheme(mainBrand);

// Tokens that drive the perceived darkness of surfaces, borders, and dividers.
// Lifting these by ~20% makes the dark theme noticeably less dark while
// preserving the overall palette and contrast relationships.
const LIGHTEN_RATIO = 0.18;
const tokensToLighten: (keyof Theme)[] = [
    "colorNeutralBackground1",
    "colorNeutralBackground1Hover",
    "colorNeutralBackground1Pressed",
    "colorNeutralBackground1Selected",
    "colorNeutralBackground2",
    "colorNeutralBackground2Hover",
    "colorNeutralBackground2Pressed",
    "colorNeutralBackground2Selected",
    "colorNeutralBackground3",
    "colorNeutralBackground3Hover",
    "colorNeutralBackground3Pressed",
    "colorNeutralBackground3Selected",
    "colorNeutralBackground4",
    "colorNeutralBackground4Hover",
    "colorNeutralBackground4Pressed",
    "colorNeutralBackground4Selected",
    "colorNeutralBackground5",
    "colorNeutralBackground5Hover",
    "colorNeutralBackground5Pressed",
    "colorNeutralBackground5Selected",
    "colorNeutralBackground6",
    "colorNeutralBackgroundAlpha",
    "colorNeutralBackgroundAlpha2",
    "colorNeutralBackgroundStatic",
    "colorNeutralBackgroundInverted",
    "colorSubtleBackground",
    "colorSubtleBackgroundHover",
    "colorSubtleBackgroundPressed",
    "colorSubtleBackgroundSelected",
    "colorNeutralStencil1",
    "colorNeutralStencil2",
    "colorNeutralStencil1Alpha",
    "colorNeutralStencil2Alpha",
    "colorBackgroundOverlay",
    "colorScrollbarOverlay",
    "colorNeutralStroke1",
    "colorNeutralStroke2",
    "colorNeutralStroke3",
];

const lightenedOverrides = tokensToLighten.reduce<Partial<Theme>>((acc, key) => {
    const value = baseDarkTheme[key];
    if (typeof value === "string" && /^#?[0-9a-f]{6}$/i.test(value)) {
        (acc as Record<string, string>)[key as string] = lighten(value, LIGHTEN_RATIO);
    }
    return acc;
}, {});

export const darkTheme: Theme = {
    ...baseDarkTheme,
    ...lightenedOverrides,
};

darkTheme.colorBrandForeground1 = mainBrand[110];
darkTheme.colorBrandForeground2 = mainBrand[120];

