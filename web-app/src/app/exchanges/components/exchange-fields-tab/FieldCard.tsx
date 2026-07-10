import {Badge, InfoLabel, Text} from '@fluentui/react-components';
import {ReactNode} from 'react';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';

interface FieldCardProps
{
    id: string;
    title: string;
    description?: string;
    required?: boolean;
    readOnly?: boolean;
    children: ReactNode;
}

const FieldCard = ({
    id,
    title,
    description,
    required = false,
    readOnly = false,
    children,
}: FieldCardProps) =>
{
    const styles = useExchangeFieldsTabStyles();

    return (
        <div id={id}
             className={styles.fieldCardItem}>
            <div className={styles.fieldCard}>
                <div className={styles.fieldCardHeader}>
                    <div className={styles.fieldCardTitleBlock}>
                        <div className={styles.fieldLabelRow}>
                            <Text className={styles.fieldLabel}>
                                {title}
                            </Text>
                            {description && (
                                <InfoLabel info={description}/>
                            )}
                        </div>
                    </div>
                    <div className={styles.fieldCardMeta}>
                        {required && (
                            <Badge appearance="tint"
                                   color="danger"
                                   size="small">
                                Required
                            </Badge>
                        )}
                        {readOnly && (
                            <Badge appearance="tint"
                                   color="warning"
                                   size="small">
                                Read only
                            </Badge>
                        )}
                    </div>
                </div>
                <div className={styles.fieldValueArea}>
                    {children}
                </div>
            </div>
        </div>
    );
};

export default FieldCard;
