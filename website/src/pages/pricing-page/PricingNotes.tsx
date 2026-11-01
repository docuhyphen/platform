import {Text} from "@fluentui/react-components";
import {usePricingPageStyles} from "./PricingPageStyles.tsx";

export function PricingNotes()
{
    const styles = usePricingPageStyles();

    return (
        <div id={"pricing-notes"}
             className={styles.notes}>
            <Text id={"pricing-note-external-clients"}>
                <strong id={"pricing-note-external-clients-title"}>External clients are not seats.</strong> Clients can complete Exchanges without
                joining your organization or purchasing an account.
            </Text>
            <Text id={"pricing-note-business-billing"}>
                <strong id={"pricing-note-business-billing-title"}>Business billing stays simple.</strong> Every active organization member is one
                paid seat.
            </Text>
            <Text id={"pricing-note-reminders"}>
                <strong id={"pricing-note-reminders-title"}>Reminders run inside a workflow.</strong> Workflow automation, including
                reminders, is a Business capability today. It is not part of the Free or Personal plans,
                and we will only publish a Personal option once it exists.
            </Text>
            <Text id={"pricing-note-currency"}>
                <strong id={"pricing-note-currency-title"}>Prices are in rand.</strong> Published prices include 15% VAT where DocuHyphen is
                required to charge VAT.
            </Text>
        </div>
    );
}
