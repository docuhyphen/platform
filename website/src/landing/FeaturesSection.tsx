import {
    Badge,
    Button,
    Card,
    CardHeader,
    Text,
    Title2,
    makeStyles,
    mergeClasses,
    tokens
} from "@fluentui/react-components";
import {
    ArrowSortDownLinesFilled,
    ChevronLeft20Regular,
    ChevronRight20Regular,
    FilterRegular,
} from "@fluentui/react-icons";
import {useState} from "react";
import {useEffect} from "react";
import {Link} from "react-router-dom";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
    WIDTH_CONTENT, WIDTH_SUBTITLE,
} from "./shared.ts";

type FeatureItem = {
    title: string;
    body: string;
    imageSrc: string;
    imageAlt: string;
};

type IndustrySlide = {
    title: string;
    visualClass: string;
    screenshotSrc: string;
    slug: string;
};

type AuditPreviewRow = {
    id: string;
    dateTimeLabel: string;
    actionWidth: string;
    personWidth: string;
};

function generateAuditPreviewRows(): AuditPreviewRow[]
{
    const formatter = new Intl.DateTimeFormat(undefined, {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    });

    return Array.from({length: 6}, (_, index) =>
    {
        const daysAgo = Math.floor(Math.random() * 30) + 1;
        const rowDate = new Date();
        rowDate.setDate(rowDate.getDate() - daysAgo);
        rowDate.setHours(8 + Math.floor(Math.random() * 10), Math.floor(Math.random() * 60), 0, 0);

        return {
            id: `audit-preview-${index}`,
            dateTimeLabel: formatter.format(rowDate),
            actionWidth: `${[74, 62, 81, 69, 77, 66][index]}%`,
            personWidth: `${[58, 72, 63, 69, 56, 74][index]}%`,
        };
    }).sort((a, b) =>
    {
        const aDate = new Date(a.dateTimeLabel).getTime();
        const bDate = new Date(b.dateTimeLabel).getTime();
        return Number.isNaN(bDate - aDate) ? 0 : bDate - aDate;
    });
}

const AUDIT_LIVE_ACTIONS: {actionWidth: string; personWidth: string}[] = [
    {actionWidth: "78%", personWidth: "62%"},
    {actionWidth: "65%", personWidth: "71%"},
    {actionWidth: "82%", personWidth: "58%"},
    {actionWidth: "70%", personWidth: "76%"},
    {actionWidth: "88%", personWidth: "55%"},
    {actionWidth: "61%", personWidth: "80%"},
];

const useStyles = makeStyles({
    introWrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        marginTop: "4rem",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        alignItems: "flex-start",
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800
    },

    carouselSection: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        width: "100%",
        display: "grid",
        gap: SPACE_MD,
    },

    carouselViewport: {
        overflow: "hidden",
        borderRadius: "3.125rem 3.125rem 0 0",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,

        [BREAKPOINT_MOBILE]: {
            borderRadius: "1.25rem 1.25rem 0 0",
        },
    },

    carouselTrack: {
        display: "flex",
        transitionProperty: "transform",
        transitionDuration: "300ms",
        transitionTimingFunction: "ease",
        willChange: "transform",
    },

    carouselSlide: {
        minWidth: "100%",
    },

    carouselImagePlaceholder: {
        aspectRatio: "1300 / 700",
        width: "100%",
        boxSizing: "border-box",
        position: "relative",
        overflow: "hidden",
        padding: "2.5rem 2.5rem 0",

        [BREAKPOINT_MOBILE]: {
            padding: "1.25rem 1.25rem 0",
        },
    },

    carouselImageFrame: {
        position: "relative",
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: "0.875rem 0.875rem 0 0",
    },

    carouselImage: {
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        objectFit: "cover",
    },

    carouselImageOverlay: {
        position: "absolute",
        left: SPACE_MD,
        bottom: SPACE_MD,
        zIndex: 1,
    },

    carouselTitleCard: {
        maxWidth: "32rem",
        padding: `${SPACE_SM} ${SPACE_MD}`,
        borderRadius: CARD_RADIUS,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow8,
        color: tokens.colorNeutralStroke2,
    },

    carouselTitleText: {
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeBase300,
            lineHeight: tokens.lineHeightBase300,
        },
    },

    carouselControls: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_MD,
    },

    carouselIndicators: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "center",
        alignItems: "center",
        gap: SPACE_XS,
    },

    carouselIndicator: {
        width: "0.625rem",
        height: "0.625rem",
        borderRadius: "50%",
        border: "none",
        cursor: "pointer",
        backgroundColor: tokens.colorNeutralStroke1,
        transitionProperty: "background-color, transform",
        transitionDuration: "200ms",
        transitionTimingFunction: "ease",
    },

    carouselIndicatorActive: {
        backgroundColor: tokens.colorBrandForeground1,
        transform: "scale(1.2)",
    },

    industryVisualLaw: {
        background: "linear-gradient(135deg, #20344d 0%, #385980 55%, #7ea6d6 100%)",
    },

    industryVisualRealEstate: {
        background: "linear-gradient(135deg, #2d3953 0%, #4b678c 52%, #99b1cf 100%)",
    },

    industryVisualAccounting: {
        background: "linear-gradient(135deg, #2e3558 0%, #5c6da9 50%, #a2b5e8 100%)",
    },

    industryVisualHealthcare: {
        background: "linear-gradient(135deg, #24505a 0%, #3f8391 52%, #8fd0dc 100%)",
    },

    industryVisualBanking: {
        background: "linear-gradient(135deg, #243f63 0%, #3f6ba1 54%, #84b5ea 100%)",
    },

    cardsWrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    cards: {
        margin: "0 auto",
        maxWidth: WIDTH_CONTENT,
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },

    featureCard: {
        // Large internal spacing so image/content don't touch card edges
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        borderRadius: "0.75rem",
        overflow: "hidden",
        backgroundColor: "#2e3558",
        borderBottomWidth: "0",
        borderBottomStyle: "none",
        borderBottomColor: "transparent",

        [BREAKPOINT_MOBILE]: {
            padding: SPACE_MD,
        },
    },

    featureThemeAccounting: {
        backgroundColor: "#2e3558",
        backgroundImage: "linear-gradient(135deg, #2e3558 0%, #5c6da9 50%, #a2b5e8 100%)",
        boxShadow: "0 10px 24px rgba(46, 53, 88, 0.28)",
    },

    featureThemeBanking: {
        backgroundColor: "#243f63",
        backgroundImage: "linear-gradient(135deg, #243f63 0%, #3f6ba1 54%, #84b5ea 100%)",
        boxShadow: "0 10px 24px rgba(36, 63, 99, 0.28)",
    },

    featureThemeLegal: {
        backgroundColor: "#20344d",
        backgroundImage: "linear-gradient(135deg, #20344d 0%, #385980 55%, #7ea6d6 100%)",
        boxShadow: "0 10px 24px rgba(32, 52, 77, 0.28)",
    },

    featureThemeRealEstate: {
        backgroundColor: "#2d3953",
        backgroundImage: "linear-gradient(135deg, #2d3953 0%, #4b678c 52%, #99b1cf 100%)",
        boxShadow: "0 10px 24px rgba(45, 57, 83, 0.28)",
    },

    featureThemeHealthcare: {
        backgroundColor: "#24505a",
        backgroundImage: "linear-gradient(135deg, #24505a 0%, #3f8391 52%, #8fd0dc 100%)",
        boxShadow: "0 10px 24px rgba(36, 80, 90, 0.28)",
    },

    featureImage: {
        width: "100%",
        height: "11rem",
        objectFit: "cover",
        display: "block",
        borderTopLeftRadius: "0.75rem",
        borderTopRightRadius: "0.75rem",
        borderBottomLeftRadius: 0,
        borderBottomRightRadius: 0,
        marginBottom: SPACE_MD,
    },

    structuredPreview: {
        width: "100%",
        height: "11rem",
        marginBottom: SPACE_MD,
        borderTopLeftRadius: "0.75rem",
        borderTopRightRadius: "0.75rem",
        borderBottomLeftRadius: 0,
        borderBottomRightRadius: 0,
        overflow: "hidden",
        backgroundColor: "#f4f6f9",
        border: "1px solid rgba(0, 0, 0, 0.08)",
        boxSizing: "border-box",
        color: "#1f2a37",
    },

    structuredToolbar: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
        padding: "0.45rem",
        borderBottom: "1px solid rgba(0, 0, 0, 0.08)",
        backgroundColor: "#eef1f5",
    },

    structuredSearch: {
        flex: 1,
        minWidth: 0,
        height: "1.65rem",
        borderRadius: "0.35rem",
        backgroundColor: "#ffffff",
        border: "1px solid rgba(0, 0, 0, 0.12)",
        display: "flex",
        alignItems: "center",
        gap: "0.35rem",
        padding: "0 0.45rem",
        boxSizing: "border-box",
    },

    structuredSearchIcon: {
        width: "0.65rem",
        height: "0.65rem",
        borderRadius: "50%",
        border: "1.5px solid #8a94a6",
        display: "inline-block",
        position: "relative",

        "::after": {
            content: "\"\"",
            position: "absolute",
            width: "0.35rem",
            height: "1.5px",
            backgroundColor: "#8a94a6",
            right: "-0.22rem",
            bottom: "-0.11rem",
            transform: "rotate(45deg)",
            transformOrigin: "center",
        },
    },

    structuredSearchText: {
        fontSize: "0.6rem",
        lineHeight: 1,
        color: "#5a6778",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    structuredControl: {
        width: "1.35rem",
        height: "1.35rem",
        borderRadius: "0.3rem",
        border: "1px solid rgba(0, 0, 0, 0.12)",
        backgroundColor: "#ffffff",
        display: "grid",
        placeItems: "center",
        fontSize: "0.52rem",
        color: "#667387",
        fontWeight: 700,
        flexShrink: 0,
    },

    structuredControlIcon: {
        fontSize: "0.75rem",
    },

    structuredList: {
        padding: "0.35rem",
        display: "grid",
        gap: "0.28rem",
    },

    structuredItem: {
        display: "grid",
        gridTemplateColumns: "1.45rem 1fr auto",
        alignItems: "start",
        columnGap: "0.35rem",
        rowGap: "0.08rem",
        backgroundColor: "#ffffff",
        borderRadius: "0.4rem",
        border: "1px solid rgba(0, 0, 0, 0.08)",
        padding: "0.36rem 0.4rem",
        boxSizing: "border-box",
        transitionProperty: "background-color, box-shadow",
        transitionDuration: "250ms",
        transitionTimingFunction: "ease",
    },

    structuredItemActive: {
        borderLeft: `2px solid ${tokens.colorBrandForeground1}`,
        paddingLeft: "0.28rem",
        backgroundColor: "#f0f5ff",
        boxShadow: `0 0 0 2px rgba(0, 102, 192, 0.12)`,
    },

    structuredAvatar: {
        width: "1.15rem",
        height: "1.15rem",
        borderRadius: "50%",
        display: "grid",
        placeItems: "center",
        fontSize: "0.55rem",
        fontWeight: 700,
        marginTop: "0.03rem",
    },

    structuredAvatarToneA: {
        backgroundColor: "#e6e8ec",
        color: "#6b7484",
    },

    structuredAvatarToneB: {
        backgroundColor: "#e2ecff",
        color: "#46608a",
    },

    structuredAvatarToneC: {
        backgroundColor: "#e7f3eb",
        color: "#3c6b4f",
    },

    structuredAvatarToneD: {
        backgroundColor: "#f5e9ff",
        color: "#6f4f89",
    },

    structuredTitle: {
        display: "block",
        width: "86%",
        height: "0.52rem",
        borderRadius: "0.28rem",
        backgroundColor: "#d6dde7",
        marginTop: "0.08rem",
    },

    structuredDate: {
        fontSize: "0.52rem",
        lineHeight: 1.2,
        color: "#5f6b7a",
        whiteSpace: "nowrap",
        marginTop: "0.05rem",
    },

    structuredSubtitle: {
        gridColumnStart: 2,
        gridColumnEnd: 4,
        display: "block",
        width: "93%",
        height: "0.46rem",
        borderRadius: "0.24rem",
        backgroundColor: "#e2e8f0",
        marginTop: "0.02rem",
    },

    securePreview: {
        width: "100%",
        height: "11rem",
        marginBottom: SPACE_MD,
        borderTopLeftRadius: "0.75rem",
        borderTopRightRadius: "0.75rem",
        borderBottomLeftRadius: 0,
        borderBottomRightRadius: 0,
        overflow: "hidden",
        border: "1px solid rgba(0, 0, 0, 0.06)",
        backgroundColor: "#eceff3",
        padding: "0.62rem 0.4rem 0 0.62rem",
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "flex-start",
        boxSizing: "border-box",
    },

    secureDialogFrame: {
        width: "100%",
        backgroundColor: "#ffffff",
        borderRadius: "0.55rem",
        border: "1px solid rgba(0, 0, 0, 0.12)",
        boxShadow: "0 2px 8px rgba(0, 0, 0, 0.12)",
        overflow: "hidden",
    },

    secureTitleBar: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "0.4rem 0.55rem",
        borderBottom: "1px solid rgba(0, 0, 0, 0.08)",
        backgroundColor: "#eef1f5",
        flexShrink: 0,
    },

    secureTitleText: {
        fontSize: "0.62rem",
        fontWeight: 700,
        color: "#2a3341",
    },

    secureTitleClose: {
        width: "0.85rem",
        height: "0.85rem",
        borderRadius: "0.18rem",
        border: "1px solid rgba(0, 0, 0, 0.1)",
        backgroundColor: "#e8eaee",
        display: "grid",
        placeItems: "center",
        fontSize: "0.5rem",
        color: "#667387",
        lineHeight: 1,
    },

    secureTabs: {
        display: "flex",
        gap: 0,
        borderBottom: "1px solid rgba(0, 0, 0, 0.08)",
        flexShrink: 0,
    },

    secureTab: {
        padding: "0.32rem 0.5rem",
        fontSize: "0.5rem",
        color: "#667387",
        borderBottom: "2px solid transparent",
        whiteSpace: "nowrap",
    },

    secureTabActive: {
        color: tokens.colorBrandForeground1,
        borderBottomColor: tokens.colorBrandForeground1,
        fontWeight: 600,
    },

    secureTabSkeleton: {
        display: "inline-block",
        height: "0.4rem",
        borderRadius: "0.2rem",
        backgroundColor: "#d6dde7",
        verticalAlign: "middle",
    },

    secureSwitchList: {
        padding: "0.38rem 0.5rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.34rem",
        flex: 1,
        overflow: "hidden",
    },

    secureSwitchRow: {
        display: "flex",
        alignItems: "center",
        gap: "0.4rem",
    },

    secureSwitchTrack: {
        width: "1.55rem",
        height: "0.82rem",
        borderRadius: "999px",
        backgroundColor: "#c8cdd5",
        position: "relative",
        flexShrink: 0,
        border: "1px solid rgba(0,0,0,0.06)",
        boxSizing: "border-box",
        transitionProperty: "background-color",
        transitionDuration: "250ms",
        transitionTimingFunction: "ease",
    },

    secureSwitchTrackOn: {
        backgroundColor: tokens.colorBrandBackground,
        border: `1px solid ${tokens.colorBrandBackground}`,
    },

    secureSwitchThumb: {
        position: "absolute",
        top: "50%",
        left: "0.1rem",
        transform: "translateY(-50%)",
        width: "0.56rem",
        height: "0.56rem",
        borderRadius: "50%",
        backgroundColor: "#ffffff",
        boxShadow: "0 1px 2px rgba(0,0,0,0.18)",
        transitionProperty: "left",
        transitionDuration: "250ms",
        transitionTimingFunction: "ease",
    },

    secureSwitchThumbOn: {
        left: "0.88rem",
    },

    secureSwitchLabel: {
        display: "inline-block",
        height: "0.4rem",
        borderRadius: "0.2rem",
        backgroundColor: "#d6dde7",
    },

    secureSwitchRowHovered: {
        backgroundColor: "rgba(0, 102, 192, 0.07)",
        borderRadius: "0.25rem",
        paddingLeft: "0.2rem",
        paddingRight: "0.2rem",
        marginLeft: "-0.2rem",
        marginRight: "-0.2rem",
        transitionProperty: "background-color",
        transitionDuration: "150ms",
        transitionTimingFunction: "ease",
    },

    auditPreview: {
        width: "100%",
        height: "11rem",
        marginBottom: SPACE_MD,
        borderTopLeftRadius: "0.75rem",
        borderTopRightRadius: "0.75rem",
        borderBottomLeftRadius: 0,
        borderBottomRightRadius: 0,
        overflow: "hidden",
        border: "1px solid rgba(0, 0, 0, 0.06)",
        backgroundColor: "#eceff3",
        padding: "0.62rem 0.4rem 0 0.62rem",
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "flex-start",
        boxSizing: "border-box",
    },

    auditDialogFrame: {
        width: "100%",
        backgroundColor: "#ffffff",
        borderRadius: "0.55rem",
        border: "1px solid rgba(0, 0, 0, 0.12)",
        boxShadow: "0 2px 8px rgba(0, 0, 0, 0.12)",
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
    },

    auditTabs: {
        display: "flex",
        gap: 0,
        borderBottom: "1px solid rgba(0, 0, 0, 0.08)",
        flexShrink: 0,
    },

    auditTab: {
        padding: "0.32rem 0.5rem",
        fontSize: "0.5rem",
        color: "#667387",
        borderBottom: "2px solid transparent",
        whiteSpace: "nowrap",
    },

    auditTabActive: {
        color: tokens.colorBrandForeground1,
        borderBottomColor: tokens.colorBrandForeground1,
        fontWeight: 600,
    },

    auditTabSkeleton: {
        display: "inline-block",
        height: "0.4rem",
        borderRadius: "0.2rem",
        backgroundColor: "#d6dde7",
        verticalAlign: "middle",
    },

    auditTableWrap: {
        padding: "0.34rem 0.44rem",
        flex: 1,
        overflow: "hidden",
    },

    auditTable: {
        width: "100%",
        borderCollapse: "collapse",
        tableLayout: "fixed",
    },

    auditTh: {
        textAlign: "left",
        fontSize: "0.48rem",
        color: "#5f6b7a",
        fontWeight: 600,
        paddingBottom: "0.2rem",
        borderBottom: "1px solid rgba(0,0,0,0.08)",
    },

    auditTd: {
        fontSize: "0.47rem",
        color: "#445062",
        padding: "0.22rem 0",
        borderBottom: "1px solid rgba(0,0,0,0.06)",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    auditSkeleton: {
        display: "inline-block",
        height: "0.38rem",
        borderRadius: "0.2rem",
        backgroundColor: "#d6dde7",
        verticalAlign: "middle",
    },

    auditRowNew: {
        animationName: {
            "0%": {opacity: 0, transform: "translateY(-6px)"},
            "100%": {opacity: 1, transform: "translateY(0)"},
        },
        animationDuration: "350ms",
        animationTimingFunction: "ease-out",
        animationFillMode: "both",
    },

    featureCardBody: {
        background: "rgba(0,0,0,0.20)",
        padding: "16px 8px",
        borderRadius: "0.6rem",
        display: "flex",
        flexDirection: "column",
        gap: "1.2rem"
    },

    featureTitleText: {
        color: "#f3f7ff",
    },

    featureBadge: {
        backgroundColor: "rgba(255, 255, 255, 0.1)",
    },

    featureBody: {
        color: "#d6e1f2",
    },

    readMoreText: {
        color: "white",
        alignSelf: "end",
        cursor: "pointer",
        textDecoration: "underline"
    },

    readMoreButton: {
        marginTop: SPACE_SM,
        color: "#f3f7ff",
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground1,
        fontWeight: "100"
    },
});

type FeaturesSectionProps = {
    initialIndustrySlug?: string;
};

const INDUSTRY_SLUG_ORDER = ["legal", "real-estate", "healthcare", "accounting", "banking"];

export function FeaturesSection({initialIndustrySlug}: FeaturesSectionProps)
{
    const styles = useStyles();
    const [activeIndustrySlide, setActiveIndustrySlide] = useState<number>(() =>
    {
        if (initialIndustrySlug)
        {
            const idx = INDUSTRY_SLUG_ORDER.indexOf(initialIndustrySlug);
            if (idx >= 0) return idx;
        }
        return 0;
    });

    useEffect(() =>
    {
        if (!initialIndustrySlug) return;
        const idx = INDUSTRY_SLUG_ORDER.indexOf(initialIndustrySlug);
        if (idx >= 0) setActiveIndustrySlide(idx);
    }, [initialIndustrySlug]);
    const [expandedFeatureTitles, setExpandedFeatureTitles] = useState<Set<string>>(() => new Set());

    // --- Feature card animation state ---
    const [activeStructuredItem, setActiveStructuredItem] = useState(0);
    const [secureSwitchStates, setSecureSwitchStates] = useState([false, false, true, false, false, false]);
    const [secureSwitchCursor, setSecureSwitchCursor] = useState(-1);
    const [auditAnimRows, setAuditAnimRows] = useState<AuditPreviewRow[]>(() => generateAuditPreviewRows());
    const [latestAuditRowId, setLatestAuditRowId] = useState<string | null>(null);

    // Cycle active item in "Structured Document Requests" card
    useEffect(() => {
        const id = setInterval(() => {
            setActiveStructuredItem(prev => (prev + 1) % 4);
        }, 2000);
        return () => clearInterval(id);
    }, []);

    // Simulate user clicking toggles in "Secure Document Delivery" card
    useEffect(() => {
        let idx = 0;
        const id = setInterval(() => {
            const i = idx % 6;
            idx++;
            setSecureSwitchCursor(i);
            setTimeout(() => {
                setSecureSwitchStates(prev => {
                    const next = [...prev];
                    next[i] = !next[i];
                    return next;
                });
                setTimeout(() => setSecureSwitchCursor(-1), 300);
            }, 500);
        }, 1800);
        return () => clearInterval(id);
    }, []);

    // Stream live rows into "Full Audit & Compliance Logging" card
    useEffect(() => {
        const formatter = new Intl.DateTimeFormat(undefined, {
            day: "2-digit",
            month: "short",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
        });
        let ai = 0;
        const id = setInterval(() => {
            const action = AUDIT_LIVE_ACTIONS[ai % AUDIT_LIVE_ACTIONS.length];
            ai++;
            const newRow: AuditPreviewRow = {
                id: `audit-live-${Date.now()}`,
                dateTimeLabel: formatter.format(new Date()),
                actionWidth: action.actionWidth,
                personWidth: action.personWidth,
            };
            setLatestAuditRowId(newRow.id);
            setAuditAnimRows(prev => [newRow, ...prev.slice(0, 5)]);
        }, 2500);
        return () => clearInterval(id);
    }, []);

    const featureThemeClasses = [
        styles.featureThemeRealEstate,
        styles.featureThemeLegal,
        styles.featureThemeHealthcare,
        styles.featureThemeAccounting,
        styles.featureThemeBanking,
    ];
    const activeThemeClass = featureThemeClasses[activeIndustrySlide] || styles.featureThemeAccounting;

    const industrySlides: IndustrySlide[] = [
        {
            title: "Law Firms & Legal Practices",
            visualClass: styles.industryVisualLaw,
            screenshotSrc: "/demo-screenshots/app-screenshot-legal.JPG",
            slug: "legal",
        },
        {
            title: "Real Estate & Property Management",
            visualClass: styles.industryVisualRealEstate,
            screenshotSrc: "/demo-screenshots/app-screenshot-real-estate.JPG",
            slug: "real-estate",
        },
        {
            title: "Healthcare & Medical Practices",
            visualClass: styles.industryVisualHealthcare,
            screenshotSrc: "/demo-screenshots/app-screenshot-healthcare.JPG",
            slug: "healthcare",
        },
        {
            title: "Accounting & Audit Firms",
            visualClass: styles.industryVisualAccounting,
            screenshotSrc: "/demo-screenshots/app-screenshot-accounting.JPG",
            slug: "accounting",
        },
        {
            title: "Banks & Lending Institutions",
            visualClass: styles.industryVisualBanking,
            screenshotSrc: "/demo-screenshots/app-screenshot-banking-lending.JPG",
            slug: "banking",
        },
    ];

    const goToPreviousIndustrySlide = () =>
    {
        setActiveIndustrySlide((current) => current === 0 ? industrySlides.length - 1 : current - 1);
    };

    const goToNextIndustrySlide = () =>
    {
        setActiveIndustrySlide((current) => (current + 1) % industrySlides.length);
    };

    const toggleFeatureExpansion = (featureTitle: string) =>
    {
        setExpandedFeatureTitles((current) =>
        {
            const updated = new Set(current);
            if (updated.has(featureTitle))
            {
                updated.delete(featureTitle);
            }
            else
            {
                updated.add(featureTitle);
            }
            return updated;
        });
    };

    const features: FeatureItem[] = [
        {
            title: "Structured Document Requests",
            body: "Create controlled share sessions to request documents from customers or partners, with defined access, expiry controls, and full visibility.",
            imageSrc: "/demo-screenshots/structured-document-requests.JPG",
            imageAlt: "Structured document requests screenshot",
        },
        {
            title: "Secure Document Delivery",
            body: "Deliver financial, legal, and confidential documents without email attachments or public links, ensuring only authorized recipients can access them.",
            imageSrc: "/demo-screenshots/secure-document-delivery.png",
            imageAlt: "Secure document delivery screenshot",
        },
        {
            title: "Full Audit & Compliance Logging",
            body: "Every upload, view, download, and action is recorded, giving you defensible audit trails and supporting regulatory compliance.",
            imageSrc: "/demo-screenshots/full-audit.JPG",
            imageAlt: "Full audit and compliance logging screenshot",
        },
    ];

    const currentDateLabel = new Intl.DateTimeFormat(undefined, {
        day: "2-digit",
        month: "short",
        year: "numeric",
    }).format(new Date());

    const structuredRequestPreviewItems = [
        {
            title: "Settlement Readiness Dossier",
            date: currentDateLabel,
            subtitle: "Counsel review package for settlement position and damages support.",
            avatarInitial: "A",
            avatarTone: styles.structuredAvatarToneB,
        },
        {
            title: "Discovery Production Coordination",
            date: currentDateLabel,
            subtitle: "Discovery document exchange review with index verification and checks.",
            avatarInitial: "K",
            avatarTone: styles.structuredAvatarToneA,
        },
        {
            title: "Regulatory Filing Support Bundle",
            date: currentDateLabel,
            subtitle: "Cross-team upload request set for filing evidence and sign-off workflows.",
            avatarInitial: "M",
            avatarTone: styles.structuredAvatarToneC,
        },
        {
            title: "Vendor Contract Intake Batch",
            date: currentDateLabel,
            subtitle: "Third-party agreement uploads with checklist validation and routing.",
            avatarInitial: "R",
            avatarTone: styles.structuredAvatarToneD,
        },
    ];

    return (
        <>
            <section className={styles.introWrapper}>
                <section className={styles.intro}>
                    <Title2 className={styles.sectionTitle}>
                        Secure, Controlled &amp; Compliant Document Exchange
                    </Title2>
                    <Text size={500} className={styles.subheading}>
                        A purpose-built platform designed specifically for exchanging
                        sensitive business and customer documents, securely, transparently,
                        and compliantly.
                    </Text>
                </section>
            </section>
            <section className={styles.carouselSection}>
                <div className={styles.carouselViewport}>
                    <div
                        className={styles.carouselTrack}
                        style={{transform: `translateX(-${activeIndustrySlide * 100}%)`}}
                    >
                        {industrySlides.map((slide) => (
                            <article key={slide.title} className={styles.carouselSlide}>
                                <Link
                                    to={`/solutions/${slide.slug}`}
                                    className={mergeClasses(styles.carouselImagePlaceholder, slide.visualClass)}
                                    aria-label={`View ${slide.title} solution`}
                                    style={{display: "block", textDecoration: "none"}}
                                >
                                    <div className={styles.carouselImageFrame}>
                                        <img
                                            className={styles.carouselImage}
                                            src={slide.screenshotSrc}
                                            alt={`${slide.title} app screenshot`}
                                            loading="lazy"
                                        />
                                        <Card
                                            className={mergeClasses(styles.carouselTitleCard, styles.carouselImageOverlay, slide.visualClass)}>
                                            <Text weight="semibold"
                                                  className={styles.carouselTitleText}>{slide.title}</Text>
                                        </Card>
                                    </div>
                                </Link>
                            </article>
                        ))}
                    </div>
                </div>

                <div className={styles.carouselControls}>
                    <Button
                        appearance="outline"
                        icon={<ChevronLeft20Regular/>}
                        shape="circular"
                        onClick={goToPreviousIndustrySlide}
                        aria-label="Show previous industry screenshot"
                    />
                    <Text>
                        {activeIndustrySlide + 1}/{industrySlides.length}
                    </Text>
                    <Button
                        appearance="outline"
                        icon={<ChevronRight20Regular/>}
                        shape="circular"
                        onClick={goToNextIndustrySlide}
                        aria-label="Show next industry screenshot"
                    />
                </div>

                <div className={styles.carouselIndicators}>
                    {industrySlides.map((slide, index) => (
                        <button
                            key={slide.title}
                            type="button"

                            className={mergeClasses(
                                styles.carouselIndicator,
                                index === activeIndustrySlide && styles.carouselIndicatorActive,
                            )}
                            onClick={() => setActiveIndustrySlide(index)}
                            aria-label={`Go to ${slide.title}`}
                            aria-current={index === activeIndustrySlide ? "true" : undefined}
                        />
                    ))}
                </div>
            </section>

            <section className={styles.cardsWrapper}>
                <div className={styles.cards}>
                    {features.map((feature) =>
                    {
                        const isExpanded = expandedFeatureTitles.has(feature.title);
                        return (
                            <Card key={feature.title} appearance="filled"
                                  id={"feature-card"}
                                  className={mergeClasses(styles.featureCard, activeThemeClass)}>
                                {feature.title === "Structured Document Requests" ? (
                                    <div className={styles.structuredPreview}
                                         aria-label="Structured document requests preview">
                                        <div className={styles.structuredToolbar}>
                                            <div className={styles.structuredSearch}>
                                                <span className={styles.structuredSearchIcon} aria-hidden="true"/>
                                                <span className={styles.structuredSearchText}>Search Document Sharing Sessions</span>
                                            </div>
                                            <span className={styles.structuredControl} aria-hidden="true">
                                            <FilterRegular className={styles.structuredControlIcon}/>
                                        </span>
                                            <span className={styles.structuredControl} aria-hidden="true">
                                            <ArrowSortDownLinesFilled className={styles.structuredControlIcon}/>
                                        </span>
                                        </div>
                                        <div className={styles.structuredList}>
                                            {structuredRequestPreviewItems.map((item, index) => (
                                                <div
                                                    key={item.title}
                                                    className={mergeClasses(styles.structuredItem, index === activeStructuredItem && styles.structuredItemActive)}
                                                >
                                                    <span
                                                        className={mergeClasses(styles.structuredAvatar, item.avatarTone)}>{item.avatarInitial}</span>
                                                    <span className={styles.structuredTitle} aria-label={item.title}/>
                                                    <span className={styles.structuredDate}>{item.date}</span>
                                                    <span className={styles.structuredSubtitle}
                                                          aria-label={item.subtitle}/>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                ) : feature.title === "Secure Document Delivery" ? (
                                    <div className={styles.securePreview} aria-label="Manage access dialog preview">
                                        <div className={styles.secureDialogFrame}>
                                            <div className={styles.secureTitleBar}>
                                                <span className={styles.secureTitleText}>Manage access</span>
                                                <span className={styles.secureTitleClose} aria-hidden="true">✕</span>
                                            </div>
                                            <div className={styles.secureTabs}>
                                            <span className={styles.secureTab}>
                                                <span className={styles.secureTabSkeleton} style={{width: "2.8rem"}}/>
                                            </span>
                                                <span
                                                    className={mergeClasses(styles.secureTab, styles.secureTabActive)}>
                                                Access &amp; permissions
                                            </span>
                                                <span className={styles.secureTab}>
                                                <span className={styles.secureTabSkeleton} style={{width: "3.6rem"}}/>
                                            </span>
                                            </div>
                                             <div className={styles.secureSwitchList}>
                                                 {secureSwitchStates.map((on, i) => (
                                                     <div key={i} className={mergeClasses(styles.secureSwitchRow, i === secureSwitchCursor && styles.secureSwitchRowHovered)}>
                                                    <span
                                                        className={mergeClasses(styles.secureSwitchTrack, on && styles.secureSwitchTrackOn)}>
                                                        <span
                                                            className={mergeClasses(styles.secureSwitchThumb, on && styles.secureSwitchThumbOn)}/>
                                                    </span>
                                                        <span
                                                            className={styles.secureSwitchLabel}
                                                            style={{width: `${[72, 58, 85, 64, 48, 76][i]}%`}}
                                                        />
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    </div>
                                ) : feature.title === "Full Audit & Compliance Logging" ? (
                                    <div className={styles.auditPreview} aria-label="Audit log dialog preview">
                                        <div className={styles.auditDialogFrame}>
                                            <div className={styles.auditTabs}>
                                            <span className={styles.auditTab}>
                                                <span className={styles.auditTabSkeleton} style={{width: "2.5rem"}}/>
                                            </span>
                                                <span className={styles.auditTab}>
                                                <span className={styles.auditTabSkeleton} style={{width: "3rem"}}/>
                                            </span>
                                                <span className={mergeClasses(styles.auditTab, styles.auditTabActive)}>Audit trail</span>
                                            </div>
                                            <div className={styles.auditTableWrap}>
                                                <table className={styles.auditTable}>
                                                    <thead>
                                                    <tr>
                                                        <th className={styles.auditTh}>Date &amp; time</th>
                                                        <th className={styles.auditTh}>Action</th>
                                                        <th className={styles.auditTh}>Person</th>
                                                    </tr>
                                                    </thead>
                                                     <tbody>
                                                     {auditAnimRows.map((row) => (
                                                         <tr key={row.id} className={row.id === latestAuditRowId ? styles.auditRowNew : undefined}>
                                                            <td className={styles.auditTd}>{row.dateTimeLabel}</td>
                                                            <td className={styles.auditTd}>
                                                                <span className={styles.auditSkeleton}
                                                                      style={{width: row.actionWidth}}/>
                                                            </td>
                                                            <td className={styles.auditTd}>
                                                                <span className={styles.auditSkeleton}
                                                                      style={{width: row.personWidth}}/>
                                                            </td>
                                                        </tr>
                                                    ))}
                                                    </tbody>
                                                </table>
                                            </div>
                                        </div>
                                    </div>
                                ) : (
                                    <img
                                        className={styles.featureImage}
                                        src={feature.imageSrc}
                                        alt={feature.imageAlt}
                                        loading="lazy"
                                    />
                                )}
                                <div className={styles.featureCardBody}>
                                    <Text weight="bold"
                                          className={styles.featureTitleText}>{feature.title}
                                    </Text>

                                    {isExpanded && <div className={styles.featureBody}>{feature.body}</div>}

                                    {isExpanded ? <></> :
                                        <Text onClick={() => toggleFeatureExpansion(feature.title)}
                                              className={styles.readMoreText}>
                                            Read more
                                        </Text>}
                                </div>
                            </Card>
                        );
                    })}
                </div>
            </section>
        </>);
}
