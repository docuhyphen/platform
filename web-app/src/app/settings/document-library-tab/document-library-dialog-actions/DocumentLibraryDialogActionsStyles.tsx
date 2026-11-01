import {GriffelStyle, makeStyles, tokens} from '@fluentui/react-components';

export const documentLibraryDialogBodyStyles = {
    dialogBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalS,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
} satisfies Record<string, GriffelStyle>;

export const useDocumentLibraryDialogActionsStyles = makeStyles({
    dialogActions: {
        display: 'flex',
        justifyContent: 'flex-end',
        gap: tokens.spacingHorizontalS,
        marginTop: tokens.spacingVerticalS,
    },
});
