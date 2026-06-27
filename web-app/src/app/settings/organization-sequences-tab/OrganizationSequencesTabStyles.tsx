import {makeStyles, tokens} from '@fluentui/react-components';

export const useOrganizationSequencesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
        padding: '0 4px',
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
        padding: '12px 16px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: '8px',
    },
    sequenceCardInner: {
        display: 'flex',
        flexDirection: 'column',
        gap: '4px',
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
        gap: '6px',
        flexWrap: 'wrap',
        alignItems: 'center',
    },
    nextText: {
        color: tokens.colorNeutralForeground3,
    },
    drawerBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
        paddingTop: '16px',
    },
    previewBox: {
        padding: '8px 12px',
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
        gap: '8px',
        justifyContent: 'flex-end',
    },
});
