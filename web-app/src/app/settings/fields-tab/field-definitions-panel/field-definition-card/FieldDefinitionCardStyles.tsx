import {makeStyles, tokens} from '@fluentui/react-components';
import {fieldsCardStyles} from '../../FieldsSharedStyles';

export const useFieldDefinitionCardStyles = makeStyles({
    card: fieldsCardStyles.card,
    cardMain: fieldsCardStyles.cardMain,
    cardFieldName: fieldsCardStyles.cardFieldName,
    cardFieldKey: fieldsCardStyles.cardFieldKey,
    cardCol2: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-end',
        justifyContent: 'center',
        flexShrink: 0,
        gap: tokens.spacingHorizontalXS,
    },
});
