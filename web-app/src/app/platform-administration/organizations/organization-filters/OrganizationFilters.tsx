import {Button, Field, Input, Select} from "@fluentui/react-components";
import {FormEvent, useState} from "react";
import {FilterIcon} from "../../../components/IconBundles.tsx";
import {useOrganizationFiltersStyles} from "./OrganizationFiltersStyles.tsx";

interface OrganizationFiltersProps
{
    disabled: boolean;
    onApply: (query: string, status: "ALL" | "ACTIVE" | "INACTIVE", tierCode: string) => void;
}

const OrganizationFilters = ({disabled, onApply}: OrganizationFiltersProps) =>
{
    const styles = useOrganizationFiltersStyles();
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
    const [tierCode, setTierCode] = useState("");

    const submit = (event: FormEvent) =>
    {
        event.preventDefault();
        onApply(query, status, tierCode);
    };

    return (
        <form
            id={"platform-organization-filters"}
            className={styles.form}
            onSubmit={submit}>
            <Field
                id={"platform-organization-search-field"}
                label={"Search"}>
                <Input
                    id={"platform-organization-search"}
                    value={query}
                    disabled={disabled}
                    placeholder={"Name or registration number"}
                    onChange={(_, data) => setQuery(data.value)}/>
            </Field>
            <Field
                id={"platform-organization-status-field"}
                label={"Account status"}>
                <Select
                    id={"platform-organization-status"}
                    value={status}
                    disabled={disabled}
                    onChange={(_, data) => setStatus(data.value as typeof status)}>
                    <option
                        id={"platform-organization-status-all"}
                        value={"ALL"}>All</option>
                    <option
                        id={"platform-organization-status-active"}
                        value={"ACTIVE"}>Active</option>
                    <option
                        id={"platform-organization-status-inactive"}
                        value={"INACTIVE"}>Inactive</option>
                </Select>
            </Field>
            <Field
                id={"platform-organization-tier-field"}
                label={"Tier code"}>
                <Input
                    id={"platform-organization-tier"}
                    value={tierCode}
                    disabled={disabled}
                    placeholder={"All tiers"}
                    onChange={(_, data) => setTierCode(data.value)}/>
            </Field>
            <Button
                id={"platform-organization-apply-filters"}
                className={styles.action}
                type={"submit"}
                appearance={"subtle"}
                shape={"circular"}
                icon={<FilterIcon/>}
                disabled={disabled}>
                Apply
            </Button>
        </form>
    );
};

export default OrganizationFilters;
