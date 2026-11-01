import {Text} from "@fluentui/react-components";
import type {SalesFormFieldsProps} from "../useSpeakToSalesForm.ts";
import {SpeakToSalesContactFields} from "./SpeakToSalesContactFields.tsx";
import {SpeakToSalesMessageFields} from "./SpeakToSalesMessageFields.tsx";
import {SpeakToSalesOrganizationFields} from "./SpeakToSalesOrganizationFields.tsx";
import {useSpeakToSalesFormStyles} from "./SpeakToSalesFormStyles.tsx";

export function SpeakToSalesForm(props: SalesFormFieldsProps)
{
    const styles = useSpeakToSalesFormStyles();

    return (
        <div id={"speak-to-sales-form"}
             className={styles.form}>
            <Text id={"speak-to-sales-contact-label"}
                  className={styles.sectionLabel}>Your details</Text>
            <SpeakToSalesContactFields {...props}/>

            <div id={"speak-to-sales-contact-divider"}
                 className={styles.divider}/>
            <Text id={"speak-to-sales-organization-label"}
                  className={styles.sectionLabel}>Organization details</Text>
            <SpeakToSalesOrganizationFields {...props}/>

            <div id={"speak-to-sales-organization-divider"}
                 className={styles.divider}/>
            <Text id={"speak-to-sales-message-label"}
                  className={styles.sectionLabel}>How can we help?</Text>
            <SpeakToSalesMessageFields {...props}/>
        </div>
    );
}
