import {makeStyles, tokens} from '@fluentui/react-components';

export const useDocumentLibraryEntryCardStyles = makeStyles({
    card: {
        minWidth: '0',
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: tokens.spacingHorizontalS,
    },
    cardBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXS,
        flex: '1',
        minWidth: '0',
    },
    descriptionText: {
        color: tokens.colorNeutralForeground2,
    },
});
