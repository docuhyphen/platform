import {DrawerBody, TabValue} from "@fluentui/react-components";
import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../../models/models.tsx";
import ExchangeDocumentComments from "../exchange-document-comments/ExchangeDocumentComments.tsx";
import ExchangeDocumentAudit from "../exchange-document-audit/ExchangeDocumentAudit.tsx";
import ExchangeDocumentVersions from "../exchange-document-versions/ExchangeDocumentVersions.tsx";
import {useExchangeDocumentSidebarBodyStyles} from "./ExchangeDocumentSidebarBodyStyles.tsx";

interface ExchangeDocumentSidebarBodyProps
{
    selectedValue: TabValue;
    exchangeDocument: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
    canViewAudit: boolean;
    canViewVersions: boolean;
    canUpload: boolean;
    canDownload: boolean;
    pageNumber?: number;
    onNavigateToPage?: (pageNumber: number) => void;
}

const ExchangeDocumentSidebarBody = (props: ExchangeDocumentSidebarBodyProps) =>
{
    const styles = useExchangeDocumentSidebarBodyStyles();
    return (
        <DrawerBody
            id={"exchange-document-sidebar-body"}
            className={styles.body}
        >
            {props.selectedValue === "comments" && (
                <ExchangeDocumentComments
                    exchangeId={props.exchange.id}
                    exchangeDocument={props.exchangeDocument}
                    pageNumber={props.pageNumber}
                    onNavigateToPage={props.onNavigateToPage}
                />
            )}
            {props.selectedValue === "versions" && props.canViewVersions && (
                <ExchangeDocumentVersions
                    exchangeId={props.exchange.id}
                    exchangeDocument={props.exchangeDocument}
                    exchange={props.exchange}
                    canUpload={props.canUpload}
                    canDownload={props.canDownload}
                />
            )}
            {props.selectedValue === "audit" && props.canViewAudit && (
                <ExchangeDocumentAudit
                    exchangeId={props.exchange.id}
                    exchangeDocument={props.exchangeDocument}
                />
            )}
        </DrawerBody>
    );
};

export default ExchangeDocumentSidebarBody;
