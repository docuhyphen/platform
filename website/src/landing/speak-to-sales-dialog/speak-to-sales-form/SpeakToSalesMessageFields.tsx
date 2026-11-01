import {Field, Textarea} from "@fluentui/react-components";
import type {SalesFormFieldsProps} from "../useSpeakToSalesForm.ts";
import {useSpeakToSalesFormStyles} from "./SpeakToSalesFormStyles.tsx";

export function SpeakToSalesMessageFields({form, errors, update, isSubmitting}: SalesFormFieldsProps)
{
    const styles = useSpeakToSalesFormStyles();

    return (
        <>
            <Field id={"speak-to-sales-message-field"}
                   label="Message"
                   validationState={errors.message ? "error" : "none"}
                   validationMessage={errors.message}>
                <Textarea id={"speak-to-sales-message"}
                          value={form.message}
                          onChange={update("message")}
                          resize="vertical"
                          rows={4}
                          disabled={isSubmitting}/>
            </Field>

            <div id={"speak-to-sales-honeypot"}
                 className={styles.honeypot}
                 aria-hidden="true">
                <label id={"speak-to-sales-website-label"}
                       htmlFor="speak-to-sales-website">
                    Do not fill this in
                    <input id={"speak-to-sales-website"}
                           type="text"
                           tabIndex={-1}
                           autoComplete="off"
                           value={form.website}
                           onChange={update("website")}/>
                </label>
            </div>
        </>
    );
}
