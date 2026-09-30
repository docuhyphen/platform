import {Caption1} from "@fluentui/react-components";
import {SubscriptionLimitsDto} from "../../../models/models.tsx";

const MEBIBYTE = 1024 * 1024;
const GIBIBYTE = 1024 * MEBIBYTE;

const byteAllowance = (bytes: number): string =>
    bytes % GIBIBYTE === 0 ? `${bytes / GIBIBYTE} GiB` : `${Math.round(bytes / MEBIBYTE)} MiB`;

interface Props
{
    limits: SubscriptionLimitsDto;
    className: string;
}

const BillingInformationRequestAllowances = ({limits, className}: Props) =>
{
    const parties = limits.maxActingPartiesPerInformationRequest;
    const files = limits.maxEvidenceFilesPerInformationRequest;
    const perRequest = limits.maxEvidenceBytesPerInformationRequest;
    const committed = limits.maxCommittedEvidenceBytes;
    if (!parties || !files || !perRequest || !committed) return null;
    const open = limits.maxOpenInformationRequests;

    return (
        <div id={"settings-billing-information-request-allowances"}
             className={className}>
            <Caption1 id={"settings-billing-information-request-open"}>
                {open != null ? `Up to ${open} open Information Requests at a time` : "Open Information Requests are not capped"}
            </Caption1>
            <Caption1 id={"settings-billing-information-request-per-request"}>
                {`${parties} acting parties, ${files} evidence files, and ${byteAllowance(perRequest)} per request`}
            </Caption1>
            <Caption1 id={"settings-billing-information-request-committed"}>
                {`${byteAllowance(committed)} of evidence across requests`}
            </Caption1>
        </div>
    );
};

export default BillingInformationRequestAllowances;
