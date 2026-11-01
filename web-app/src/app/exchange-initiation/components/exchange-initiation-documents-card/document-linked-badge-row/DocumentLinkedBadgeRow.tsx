import React from 'react';
import {Badge, Button} from "@fluentui/react-components";
import {useExchangeInitiationStyles} from "../../../ExchangeInitiationStyles.tsx";
import {LinkDismissIcon} from "../../../../components/IconBundles.tsx";

interface DocumentLinkedBadgeRowProps
{
    index: number;
    onUnlink?: (index: number) => void;
}

const DocumentLinkedBadgeRow: React.FC<DocumentLinkedBadgeRowProps> = ({index, onUnlink}) =>
{
    const styles = useExchangeInitiationStyles();

    return (
        <div id={"exchange-initiation-documents-card-div-3"}
             className={styles.docLinkedBadgeRow}>
            <Badge id={"exchange-initiation-documents-card-badge-1"}
                   appearance="tint"
                   color="success"
                   size="small">
                Linked from library
            </Badge>
            {onUnlink && (
                <Button
                    id={`doc-card-unlink-btn-${index}`}
                    size="small"
                    appearance="outline"
                    shape="circular"
                    icon={<LinkDismissIcon/>}
                    onClick={() => onUnlink(index)}
                >
                    Unlink
                </Button>
            )}
        </div>
    );
};

export default DocumentLinkedBadgeRow;
