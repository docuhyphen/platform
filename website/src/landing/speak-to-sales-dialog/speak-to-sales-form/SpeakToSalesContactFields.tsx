import {Field, Input} from "@fluentui/react-components";
import type {SalesFormFieldsProps} from "../useSpeakToSalesForm.ts";
import {useSpeakToSalesFormStyles} from "./SpeakToSalesFormStyles.tsx";

export function SpeakToSalesContactFields({form, errors, update, isSubmitting}: SalesFormFieldsProps)
{
    const styles = useSpeakToSalesFormStyles();

    return (
        <>
            <div id={"speak-to-sales-name-row"}
                 className={styles.row}>
                <Field id={"speak-to-sales-first-name-field"}
                       label="First name"
                       required
                       validationState={errors.firstName ? "error" : "none"}
                       validationMessage={errors.firstName}>
                    <Input id={"speak-to-sales-first-name"}
                           value={form.firstName}
                           onChange={update("firstName")}
                           disabled={isSubmitting}/>
                </Field>
                <Field id={"speak-to-sales-last-name-field"}
                       label="Last name"
                       required
                       validationState={errors.lastName ? "error" : "none"}
                       validationMessage={errors.lastName}>
                    <Input id={"speak-to-sales-last-name"}
                           value={form.lastName}
                           onChange={update("lastName")}
                           disabled={isSubmitting}/>
                </Field>
            </div>

            <div id={"speak-to-sales-contact-row"}
                 className={styles.row}>
                <Field id={"speak-to-sales-work-email-field"}
                       label="Work email"
                       required
                       validationState={errors.workEmail ? "error" : "none"}
                       validationMessage={errors.workEmail}>
                    <Input id={"speak-to-sales-work-email"}
                           type="email"
                           value={form.workEmail}
                           onChange={update("workEmail")}
                           disabled={isSubmitting}/>
                </Field>
                <Field id={"speak-to-sales-phone-field"}
                       label="Phone number">
                    <Input id={"speak-to-sales-phone"}
                           type="tel"
                           value={form.phone}
                           onChange={update("phone")}
                           disabled={isSubmitting}/>
                </Field>
            </div>
        </>
    );
}
