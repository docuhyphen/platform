import {Field, Input, Select} from "@fluentui/react-components";
import type {SalesFormFieldsProps} from "../useSpeakToSalesForm.ts";
import {useSpeakToSalesFormStyles} from "./SpeakToSalesFormStyles.tsx";

const organizationTypes = [
    "Banking / Lending",
    "Healthcare / Medical",
    "Accounting / Audit",
    "Real Estate / Property",
    "Legal / Law Firm",
    "Insurance",
    "Technology",
    "Other",
];

const companySizes = [
    "1-10 employees",
    "11-50 employees",
    "51-200 employees",
    "201-1,000 employees",
    "1,000+ employees",
];

export function SpeakToSalesOrganizationFields({form, errors, update, isSubmitting}: SalesFormFieldsProps)
{
    const styles = useSpeakToSalesFormStyles();

    return (
        <>
            <div id={"speak-to-sales-organization-row"}
                 className={styles.row}>
                <Field id={"speak-to-sales-organization-name-field"}
                       label="Organization name"
                       required
                       validationState={errors.organizationName ? "error" : "none"}
                       validationMessage={errors.organizationName}>
                    <Input id={"speak-to-sales-organization-name"}
                           value={form.organizationName}
                           onChange={update("organizationName")}
                           disabled={isSubmitting}/>
                </Field>
                <Field id={"speak-to-sales-country-field"}
                       label="Country / Region">
                    <Input id={"speak-to-sales-country"}
                           value={form.country}
                           onChange={update("country")}
                           disabled={isSubmitting}/>
                </Field>
            </div>

            <div id={"speak-to-sales-organization-profile-row"}
                 className={styles.row}>
                <Field id={"speak-to-sales-organization-type-field"}
                       label="Organization type"
                       required
                       validationState={errors.organizationType ? "error" : "none"}
                       validationMessage={errors.organizationType}>
                    <Select id={"speak-to-sales-organization-type"}
                            value={form.organizationType}
                            onChange={update("organizationType")}
                            disabled={isSubmitting}>
                        <option id={"speak-to-sales-organization-type-placeholder"}
                                value=""
                                disabled>Select a type</option>
                        {organizationTypes.map((organizationType, index) => (
                            <option id={`speak-to-sales-organization-type-option-${index + 1}`}
                                    key={organizationType}
                                    value={organizationType}>{organizationType}</option>
                        ))}
                    </Select>
                </Field>
                <Field id={"speak-to-sales-company-size-field"}
                       label="Company size"
                       required
                       validationState={errors.companySize ? "error" : "none"}
                       validationMessage={errors.companySize}>
                    <Select id={"speak-to-sales-company-size"}
                            value={form.companySize}
                            onChange={update("companySize")}
                            disabled={isSubmitting}>
                        <option id={"speak-to-sales-company-size-placeholder"}
                                value=""
                                disabled>Select a size</option>
                        {companySizes.map((companySize, index) => (
                            <option id={`speak-to-sales-company-size-option-${index + 1}`}
                                    key={companySize}
                                    value={companySize}>{companySize}</option>
                        ))}
                    </Select>
                </Field>
            </div>
        </>
    );
}
