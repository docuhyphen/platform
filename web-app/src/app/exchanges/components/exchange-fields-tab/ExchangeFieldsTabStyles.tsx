import {makeStyles, tokens} from '@fluentui/react-components';

export const useExchangeFieldsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
        padding: '8px 4px 20px',
    },
    headerRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: '16px',
        flexWrap: 'wrap',
    },
    headerTitleBlock: {
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
        minWidth: 0,
    },
    sectionEyebrow: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
    },
    schemaTitle: {
        fontSize: tokens.fontSizeBase500,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
    },
    schemaSummaryCard: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: '16px',
        flexWrap: 'wrap',
        padding: '18px 20px',
        borderRadius: tokens.borderRadiusXLarge,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },
    headerDescription: {
        color: tokens.colorNeutralForeground2,
        maxWidth: '720px',
        lineHeight: tokens.lineHeightBase300,
    },
    subText: {
        color: tokens.colorNeutralForeground3,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    emptyState: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
        alignItems: 'flex-start',
        padding: '12px 0',
    },
    assignRow: {
        display: 'flex',
        gap: '8px',
        alignItems: 'flex-end',
        flexWrap: 'wrap',
    },
    grow: {
        flex: '1',
        minWidth: '220px',
    },
    fieldList: {
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
    },
    sectionBlock: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
    },
    sectionHeader: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '12px',
        flexWrap: 'wrap',
    },
    sectionTitle: {
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
    },
    fieldLane: {
        display: 'flex',
        flexWrap: 'wrap',
        gap: '16px',
        alignItems: 'stretch',
    },
    fieldCardItem: {
        minWidth: 0,
        flex: '1 1 100%',
        maxWidth: '100%',
        '@media (min-width: 769px)': {
            flex: '1 1 calc((100% - 16px) / 2)',
            maxWidth: 'calc((100% - 16px) / 2)',
        },
        '@media (min-width: 1401px)': {
            flex: '1 1 calc((100% - 32px) / 3)',
            maxWidth: 'calc((100% - 32px) / 3)',
        },
    },
    fieldCard: {
        display: 'flex',
        flexDirection: 'column',
        gap: '14px',
        minWidth: 0,
        padding: '16px',
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },
    fieldCardWide: {
        '@media (min-width: 769px)': {
            flexBasis: '100%',
            maxWidth: '100%',
        },
        '@media (min-width: 1401px)': {
            flexBasis: 'calc((((100% - 32px) / 3) * 2) + 16px)',
            maxWidth: 'calc((((100% - 32px) / 3) * 2) + 16px)',
        },
    },
    fieldCardHeader: {
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: '12px',
        flexWrap: 'wrap',
    },
    fieldCardTitleBlock: {
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
        minWidth: 0,
        flex: 1,
    },
    fieldCardMeta: {
        display: 'flex',
        gap: '8px',
        flexWrap: 'wrap',
        alignItems: 'center',
    },
    fieldLabel: {
        fontSize: tokens.fontSizeBase300,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
    },
    fieldDescription: {
        color: tokens.colorNeutralForeground3,
        lineHeight: tokens.lineHeightBase300,
    },
    fieldValueArea: {
        display: 'flex',
        flexDirection: 'column',
        gap: '10px',
        minWidth: 0,
    },
    label: {
        fontWeight: tokens.fontWeightSemibold,
    },
    fieldValueText: {
        fontSize: tokens.fontSizeBase400,
        lineHeight: tokens.lineHeightBase400,
        color: tokens.colorNeutralForeground1,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
    },
    fieldValuePlaceholder: {
        color: tokens.colorNeutralForeground3,
        fontStyle: 'italic',
    },
    valueBadgeRow: {
        display: 'flex',
        gap: '8px',
        flexWrap: 'wrap',
    },
    editorField: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
    },
    buttonRow: {
        display: 'flex',
        gap: '8px',
        justifyContent: 'flex-end',
        flexWrap: 'wrap',
    },
    schemaBadgeRow: {
        display: 'flex',
        gap: '8px',
        alignItems: 'center',
        flexWrap: 'wrap',
    },
});
