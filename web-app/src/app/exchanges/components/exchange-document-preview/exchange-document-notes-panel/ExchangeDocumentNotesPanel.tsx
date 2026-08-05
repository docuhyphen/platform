import {Button, Text, Tooltip} from "@fluentui/react-components";
import {DismissIcon} from "../../../../components/IconBundles.tsx";
import {DocumentDetailedDto} from "../../../../models/models.tsx";
import ExchangeDocumentComments from "../../exchange-document-sidebar/exchange-document-comments/ExchangeDocumentComments.tsx";
import {useExchangeDocumentNotesPanelStyles} from "./ExchangeDocumentNotesPanelStyles.tsx";

interface ExchangeDocumentNotesPanelProps
{
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    onClose: () => void;
}

const ExchangeDocumentNotesPanel = (
    {
        exchangeId,
        exchangeDocument,
        onClose,
    }: ExchangeDocumentNotesPanelProps) =>
{
    const styles = useExchangeDocumentNotesPanelStyles();

    return (
        <aside
            id={"exchange-document-preview-notes-panel"}
            className={styles.panel}
            aria-label={"Document notes and comments"}
        >
            <div
                id={"exchange-document-preview-notes-header"}
                className={styles.header}
            >
                <Text
                    id={"exchange-document-preview-notes-title"}
                    size={400}
                    weight={"semibold"}
                >
                    Notes and comments
                </Text>
                <Tooltip
                    content={"Close notes and comments"}
                    relationship={"description"}
                >
                    <Button
                        id={"exchange-document-preview-notes-close"}
                        aria-label={"Close notes and comments"}
                        appearance={"transparent"}
                        shape={"circular"}
                        icon={<DismissIcon/>}
                        onClick={onClose}
                    />
                </Tooltip>
            </div>
            <div
                id={"exchange-document-preview-notes-content"}
                className={styles.content}
            >
                <ExchangeDocumentComments
                    exchangeId={exchangeId}
                    exchangeDocument={exchangeDocument}
                    idPrefix={"exchange-document-preview"}
                />
            </div>
        </aside>
    );
};

export default ExchangeDocumentNotesPanel;
