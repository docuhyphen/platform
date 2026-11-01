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
            <div id={"field-card-div-1"}
                 className={styles.fieldCard}>
                <div id={"field-card-div-2"}
                     className={styles.fieldCardHeader}>
                    <div id={"field-card-div-3"}
                         className={styles.fieldCardTitleBlock}>
                        <div id={"field-card-div-4"}
                             className={styles.fieldLabelRow}>
                            <Text id={"field-card-text-1"}
                                  className={styles.fieldLabel}>
                                {title}
                            </Text>
                            {description && (
                                <InfoLabel id={"field-card-info-label-1"}
                                           info={description}/>
                            )}
                        </div>
                    </div>
                    <div id={"field-card-div-5"}
                         className={styles.fieldCardMeta}>
                        {required && (
                            <Badge id={"field-card-badge-1"}
                                   appearance="tint"
                                   color="danger"
                                   size="small">
                                Required
                            </Badge>
                        )}
                        {readOnly && (
                            <Badge id={"field-card-badge-2"}
                                   appearance="tint"
                                   color="warning"
                                   size="small">
                                Read only
                            </Badge>
                        )}
                    </div>
                </div>
                <div id={"field-card-div-6"}
                     className={styles.fieldValueArea}>
                    {children}
                </div>
            </div>
        </div>
    );
};

export default FieldCard;
