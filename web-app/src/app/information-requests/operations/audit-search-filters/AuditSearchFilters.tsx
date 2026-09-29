import {Button} from "@fluentui/react-components";
import DateTimeField from "../../shared/date-time-field/DateTimeField.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {AuditSearchForm} from "../audit-search/useAuditSearch.ts";
import {useAuditSearchFiltersStyles} from "./AuditSearchFiltersStyles.tsx";

interface AuditSearchFiltersProps
{
    form: AuditSearchForm;
    problem?: string;
    onChange: (field: keyof AuditSearchForm, value: string) => void;
    onSearch: () => void;
}

const AuditSearchFilters = ({form, problem, onChange, onSearch}: AuditSearchFiltersProps) =>
{
    const styles = useAuditSearchFiltersStyles();

    return (
        <form id={"information-request-audit-search-filters"}
              role={"search"}
              aria-label={"Search the audit record"}
              onSubmit={formEvent =>
              {
                  formEvent.preventDefault();
                  if (!problem) onSearch();
              }}>
            <div id={"information-request-audit-search-fields"}
                 className={styles.filters}>
                <TextField id={"information-request-audit-search-request"}
                           label={"Request id"}
                           hint={"The full id of one request."}
                           value={form.requestId}
                           onChange={value => onChange("requestId", value)}/>
                <TextField id={"information-request-audit-search-class"}
                           label={"Event class"}
                           hint={"Such as party, review, or clock."}
                           value={form.eventClass}
                           onChange={value => onChange("eventClass", value)}/>
                <TextField id={"information-request-audit-search-type"}
                           label={"Event type"}
                           hint={"The full event key."}
                           value={form.eventType}
                           onChange={value => onChange("eventType", value)}/>
                <TextField id={"information-request-audit-search-actor"}
                           label={"Actor id"}
                           hint={"The id of the person or service that acted."}
                           value={form.actorId}
                           onChange={value => onChange("actorId", value)}/>
                <DateTimeField id={"information-request-audit-search-from"}
                               label={"From"}
                               value={form.from}
                               onChange={value => onChange("from", value)}/>
                <DateTimeField id={"information-request-audit-search-until"}
                               label={"Until"}
                               value={form.until}
                               validationMessage={problem}
                               onChange={value => onChange("until", value)}/>
            </div>
            <div id={"information-request-audit-search-actions"}
                 className={styles.actions}>
                <Button id={"information-request-audit-search-submit"}
                        type={"submit"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={Boolean(problem)}>
                    Search
                </Button>
            </div>
        </form>
    );
};

export default AuditSearchFilters;
