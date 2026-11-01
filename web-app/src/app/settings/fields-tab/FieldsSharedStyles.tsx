import {GriffelStyle, tokens} from '@fluentui/react-components';

export const fieldsStatusTextStyles = {
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
    },
} satisfies Record<string, GriffelStyle>;

export const fieldsCardStyles = {
    cardGrid: {
        display: 'grid',
        gridTemplateColumns: 'repeat(2, 1fr)',
        gap: tokens.spacingHorizontalM,
        '@media screen and (max-width: 768px)': {
            gridTemplateColumns: '1fr',
        },
    },
    card: {
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'stretch',
        gap: tokens.spacingHorizontalS,
    },
    cardMain: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXS,
        minWidth: '0',
        flex: '1',
    },
    cardFieldName: {
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeBase300,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
    cardFieldKey: {
        fontFamily: 'monospace',
        fontSize: tokens.fontSizeBase200,
        color: tokens.colorNeutralForeground2,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
} satisfies Record<string, GriffelStyle>;

export const fieldsTableStyles = {
    table: {
        width: '100%',
        borderCollapse: 'collapse',
    },
    th: {
        textAlign: 'left',
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalM}`,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke1}`,
        whiteSpace: 'nowrap',
    },
    tr: {
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        ':hover': {
            backgroundColor: tokens.colorNeutralBackground2,
        },
    },
    td: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        verticalAlign: 'middle',
    },
} satisfies Record<string, GriffelStyle>;

export const fieldsFormStyles = {
    drawerBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalL,
    },
    fieldGroup: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalM,
    },
    twoColumn: {
        display: 'flex',
        gap: tokens.spacingHorizontalM,
        flexWrap: 'wrap',
    },
    grow: {
        flex: '1',
        minWidth: '160px',
    },
} satisfies Record<string, GriffelStyle>;
