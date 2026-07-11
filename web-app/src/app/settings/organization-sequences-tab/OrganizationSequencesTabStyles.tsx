import {makeStyles, tokens} from '@fluentui/react-components';

export const useOrganizationSequencesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        padding: `0 ${tokens.spacingHorizontalXS}`,
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    descriptionText: {
        color: tokens.colorNeutralForeground3,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
    },
    sequenceCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: tokens.spacingHorizontalS,
    },
    sequenceCardInner: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXS,
        flex: '1',
        minWidth: '0',
    },
    codeToken: {
        fontFamily: 'monospace',
        fontSize: '12px',
        color: tokens.colorNeutralForeground2,
    },
    badgeRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalSNudge,
        flexWrap: 'wrap',
        alignItems: 'center',
    },
    nextText: {
        color: tokens.colorNeutralForeground3,
    },
    drawerBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalL,
    },
    previewBox: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        background: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusLarge,
    },
    previewLabel: {
        color: tokens.colorNeutralForeground3,
    },
    formError: {
        color: tokens.colorPaletteRedForeground1,
    },
    buttonRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalS,
        justifyContent: 'flex-end',
    },
    toolbar: {
        display: 'flex',
        justifyContent: 'flex-end',
    },
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
        borderBottom: `1px solid ${tokens.colorNeutralStroke1}`,
        whiteSpace: 'nowrap',
    },
    tr: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ':hover': {backgroundColor: tokens.colorNeutralBackground2},
    },
    td: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        verticalAlign: 'middle',
    },
});
