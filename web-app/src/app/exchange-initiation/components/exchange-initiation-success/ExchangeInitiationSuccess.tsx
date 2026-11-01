import React from 'react';
import {Button, Text} from "@fluentui/react-components";
import CreatedInformationRequestSummary
    from "../created-information-request-summary/CreatedInformationRequestSummary.tsx";
import {CreatedExchangeSummary, ExchangeInitiationCopyLinkStatus} from "../../exchangeInitiationTypes.ts";
import {useExchangeInitiationSuccessStyles} from "./ExchangeInitiationSuccessStyles.tsx";

interface ExchangeInitiationSuccessProps
{
    summary: CreatedExchangeSummary | null;
    fallbackName: string;
    fallbackDocumentCount: number;
    fallbackRequiresSignIn: boolean;
    copyLinkStatus: ExchangeInitiationCopyLinkStatus;
    onCopyLink: () => void;
    onViewExchange: () => void;
    onManageInformationRequest: (requestId: string) => void;
}

const ExchangeInitiationSuccess: React.FC<ExchangeInitiationSuccessProps> = (
    {
        summary,
        fallbackName,
        fallbackDocumentCount,
        fallbackRequiresSignIn,
        copyLinkStatus,
        onCopyLink,
        onViewExchange,
        onManageInformationRequest,
    }) =>
{
    const styles = useExchangeInitiationSuccessStyles();
    const requiresSignIn = summary?.requiresSignIn ?? fallbackRequiresSignIn;

    return (
        <div id={"exchange-initiation-success"}
             className={styles.exchangeInitiationSuccess}>
            <Text id={"exchange-initiation-exchange-started-successfully-text"}
                  size={500}> Exchange started successfully </Text>
            <Text id={"exchange-initiation-name-text"}
                  size={300}
                  italic={true}> {summary?.name || fallbackName} </Text>
            <div id={"exchange-initiation-exchange-success-details"}
                 className={styles.exchangeSuccessDetails}>
                <Text id={"exchange-initiation-recipient-text"}
                      size={200}>Recipient</Text>
                <Text id={"exchange-initiation-recipient-text-2"}
                      size={200}>{summary?.recipient || '-'}</Text>
                <Text id={"exchange-initiation-documents-text"}
                      size={200}>Documents</Text>
                <Text id={"exchange-initiation-length-text"}
                      size={200}>{summary?.documents ?? fallbackDocumentCount}</Text>
                <Text id={"exchange-initiation-recipient-sign-in-text"}
                      size={200}>Recipient sign-in</Text>
                <Text id={"exchange-initiation-required-text"}
                      size={200}>{requiresSignIn ? 'Required' : 'Not required'}</Text>
            </div>
            {summary?.informationRequest !== undefined && (
                <CreatedInformationRequestSummary request={summary.informationRequest}
                                                  onManage={onManageInformationRequest}/>
            )}
            <div id={"exchange-initiation-exchange-success-actions"}
                 className={styles.exchangeSuccessActions}>
                <Button id={"exchange-copy-link-btn"}
                        appearance={"subtle"}
                        shape={"circular"}
                        onClick={onCopyLink}
                        disabled={!summary?.shareLink}>
                    Copy Link
                </Button>
                <Button id={"exchange-view-exchange-btn"}
                        shape={"circular"}
                        appearance={"subtle"}
                        onClick={onViewExchange}
                        disabled={!summary?.id}>
                    View Exchange
                </Button>
            </div>
            {copyLinkStatus === 'copied' && <Text id={"exchange-initiation-link-copied-text"}
                                                  size={200}>Link copied</Text>}
            {copyLinkStatus === 'failed' && <Text id={"exchange-initiation-could-not-copy-link-text"}
                                                  size={200}>Could not copy link</Text>}
        </div>
    );
};

export default ExchangeInitiationSuccess;
