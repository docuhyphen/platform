import {Badge, Button, Card, Field, Input} from '@fluentui/react-components';
import {AvailableVariablesDto, BlueprintDocumentConfig} from '../../../../models/models.tsx';
import {DeleteIcon, LinkDismissIcon} from '../../../../components/IconBundles.tsx';
import {useExchangeInitiationStyles} from '../../../../exchange-initiation/ExchangeInitiationStyles.tsx';
import VariableTokenInput from '../../../../../components/variable-token-input/VariableTokenInput.tsx';
import BlueprintDocumentRestrictions
    from './blueprint-document-restrictions/BlueprintDocumentRestrictions.tsx';
import {useBlueprintDocumentCardStyles} from './BlueprintDocumentCardStyles.tsx';

interface BlueprintDocumentCardProps
{
    index: number;
    document: BlueprintDocumentConfig;
    availableVariables: AvailableVariablesDto | null;
    onChange: (patch: Partial<BlueprintDocumentConfig>) => void;
    onRemove: () => void;
}

const BlueprintDocumentCard = (
    {
        index: i,
        document: doc,
        availableVariables,
        onChange,
        onRemove,
    }: BlueprintDocumentCardProps) =>
{
    const styles = useExchangeInitiationStyles();
    const cardStyles = useBlueprintDocumentCardStyles();
    const linked = !!doc.libraryDocumentId;

    return (
        <Card
            id={`blueprint-editor-shading-exchange-document-card-${i}`}
            className={styles.shadingExchangeDocumentCard}
        >
            <div id={`blueprint-editor-div-3-${i}`}>
                <div id={`blueprint-editor-dialog-title1-${i}`}
                     className={styles.dialogTitle1}>
                    <Field id={`blueprint-editor-sharing-details-input-${i}`}
                           className={styles.sharingDetailsInput}>
                        {availableVariables ? (
                            <VariableTokenInput
                                value={doc.title}
                                onChange={v => onChange({title: v})}
                                availableVariables={availableVariables}
                                placeholder="Document name, type {{ to insert a variable"
                                disabled={linked}
                            />
                        ) : (
                            <Input
                                id={`bp-doc-title-${i}`}
                                type="text"
                                size="small"
                                value={doc.title}
                                onChange={(_, d) => onChange({title: d.value})}
                                placeholder="Document name"
                                disabled={linked}
                            />
                        )}
                    </Field>
                    <Button
                        id={`bp-doc-delete-${i}`}
                        icon={<DeleteIcon className={styles.iconDeleteFilled}/>}
                        appearance="transparent"
                        shape="circular"
                        onClick={onRemove}
                    />
                </div>
                {doc.libraryDocumentId && (
                    <div id={`blueprint-editor-shading-exchange-document-card-div-${i}`}
                         className={cardStyles.linkedBadgeRow}>
                        <Badge
                            id={`bp-doc-linked-badge-${i}`}
                            appearance="tint"
                            color="success"
                            size="small"
                        >
                            Linked from library
                        </Badge>
                        <Button
                            id={`bp-doc-unlink-${i}`}
                            size="small"
                            appearance="subtle"
                            icon={<LinkDismissIcon/>}
                            shape="circular"
                            onClick={() => onChange({libraryDocumentId: undefined})}
                        >
                            Unlink
                        </Button>
                    </div>
                )}
                <BlueprintDocumentRestrictions index={i}
                                               document={doc}
                                               onChange={onChange}/>
            </div>
        </Card>
    );
};

export default BlueprintDocumentCard;
