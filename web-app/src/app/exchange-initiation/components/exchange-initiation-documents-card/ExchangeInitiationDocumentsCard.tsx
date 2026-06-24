import React from 'react';
import {Badge, Button, Card, Checkbox, Dropdown, Field, Input, Option, OptionGroup, Switch} from "@fluentui/react-components";
import {AvailableVariablesDto, DocumentType, ExchangeRequestDocumentRequest, ImageType} from "../../../models/models.tsx";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {DeleteIcon} from "../../../components/IconBundles.tsx";
import VariableTokenInput from "../../../../components/variable-token-input/VariableTokenInput.tsx";

interface DocumentCardProps
{
    document: ExchangeRequestDocumentRequest;
    index: number;
    onDocumentNameChange: (index: number, newValue: string) => void;
    onDocumentTypeChange: (index: number, newType: DocumentType | ImageType) => void;
    onRestrictDocumentTypeChange: (index: number, ev: React.ChangeEvent<HTMLInputElement>) => void;
    onDeleteDocument: (index: number) => void;
    onRequiredChange: (index: number, required: boolean) => void;
    onUnlink?: (index: number) => void;
    availableVariables?: AvailableVariablesDto;
}

const ExchangeInitiationDocumentsCard: React.FC<DocumentCardProps> = (
    {
        document,
        index,
        onDocumentNameChange,
        onDocumentTypeChange,
        onRestrictDocumentTypeChange,
        onDeleteDocument,
        onRequiredChange,
        onUnlink,
        availableVariables,
    }) =>
{
    const styles = useExchangeInitiationStyles();
    const isLinked = !!document.libraryDocumentId;

    return (
        <Card key={index} className={styles.shadingExchangeDocumentCard}>
            <div>
                <div className={styles.dialogTitle1}>
                    <Field className={styles.sharingDetailsInput}>
                        {availableVariables ? (
                            <VariableTokenInput
                                value={document.title || ''}
                                onChange={v => onDocumentNameChange(index, v)}
                                availableVariables={availableVariables}
                                placeholder="Document name, type {{ to insert a variable"
                                disabled={isLinked}
                            />
                        ) : (
                            <Input
                                type="text"
                                size="small"
                                value={document.title || ''}
                                required
                                onChange={(e) => onDocumentNameChange(index, e.target.value)}
                                placeholder="Document name"
                                disabled={isLinked}
                            />
                        )}
                    </Field>
                    <Button
                        icon={<DeleteIcon className={styles.iconDeleteFilled}/>}
                        appearance="subtle"
                        onClick={() => onDeleteDocument(index)}
                    />
                </div>
                {isLinked && (
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px', marginTop: '6px'}}>
                        <Badge appearance="tint" color="success" size="small">
                            Linked from library
                        </Badge>
                        {onUnlink && (
                            <Button
                                size="small"
                                appearance="subtle"
                                shape="circular"
                                onClick={() => onUnlink(index)}
                            >
                                Unlink
                            </Button>
                        )}
                    </div>
                )}
                <div className={styles.exchangeDocumentsRestriction}>
                    <div className={styles.exchangeDocumentsRestrictionField}>
                        <Field label="">
                            <Switch
                                label="Restrict upload type"
                                checked={document.restrictType ?? false}
                                disabled={isLinked}
                                onChange={(ev) => onRestrictDocumentTypeChange(index, ev)}
                            />
                        </Field>
                        <Dropdown
                            className={styles.exchangeDocumentsDropdown}
                            disabled={!document.restrictType || isLinked}
                            appearance="underline"
                            value={document.restrictedType ?? ''}
                            size="small"
                            placeholder="Select allowed upload type"
                            onOptionSelect={(_e, data) => onDocumentTypeChange(index, data.optionValue as any)}
                        >
                            <OptionGroup label="Documents">
                                {Object.values(DocumentType).map((option) => (
                                    <Option key={option} value={option}>
                                        {option}
                                    </Option>
                                ))}
                            </OptionGroup>
                            <OptionGroup label="Images">
                                {Object.values(ImageType).map((option) => (
                                    <Option key={option} value={option}>
                                        {option}
                                    </Option>
                                ))}
                            </OptionGroup>
                        </Dropdown>
                    </div>
                    <Checkbox
                        label="Required"
                        checked={document.required ?? false}
                        disabled={isLinked}
                        onChange={(_, d) => onRequiredChange(index, !!d.checked)}
                    />
                </div>
            </div>
        </Card>
    );
};

export default ExchangeInitiationDocumentsCard;
