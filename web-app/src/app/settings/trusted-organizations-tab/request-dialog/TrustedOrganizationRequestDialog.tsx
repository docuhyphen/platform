import {
    Button, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface,
    DialogTitle, Field, Input, Spinner, Textarea,
} from "@fluentui/react-components";
import {useEffect, useRef, useState} from "react";
import {OrganizationDirectoryEntry, searchOrganizationsForTrust} from "../../../../services/organizationTrust.ts";
import OrganizationSearchResults from "./organization-search-results/OrganizationSearchResults.tsx";
import {useTrustedOrganizationRequestDialogStyles} from "./TrustedOrganizationRequestDialogStyles.tsx";
interface TrustedOrganizationRequestDialogProps
{
    open: boolean;
    busy: boolean;
    onDismiss: () => void;
    onSubmit: (organizationId: string, message?: string) => Promise<void>;
}
const TrustedOrganizationRequestDialog = ({open, busy, onDismiss, onSubmit}: TrustedOrganizationRequestDialogProps) =>
{
    const styles = useTrustedOrganizationRequestDialogStyles();
    const [query, setQuery] = useState("");
    const [message, setMessage] = useState("");
    const [results, setResults] = useState<OrganizationDirectoryEntry[]>([]);
    const [selected, setSelected] = useState<OrganizationDirectoryEntry | null>(null);
    const [searching, setSearching] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const searchSequence = useRef(0);
    useEffect(() =>
    {
        if (!open)
        {
            searchSequence.current += 1;
            setQuery("");
            setMessage("");
            setResults([]);
            setSelected(null);
            setError(null);
        }
    }, [open]);
    const search = async () =>
    {
        const sequence = ++searchSequence.current;
        setSearching(true);
        setError(null);
        try
        {
            const nextResults = await searchOrganizationsForTrust(query);
            if (sequence === searchSequence.current) setResults(nextResults);
        }
        catch (requestError: unknown)
        {
            if (sequence === searchSequence.current)
                setError(requestError instanceof Error ? requestError.message : "Organization search failed");
        }
        finally
        {
            if (sequence === searchSequence.current) setSearching(false);
        }
    };
    return (
        <Dialog
            id={"trusted-organization-request-dialog-root"}
            open={open}
            onOpenChange={(_, data) => !data.open && onDismiss()}
        >
            <DialogSurface id={"trusted-organization-request-dialog"}>
                <DialogBody id={"trusted-organization-request-dialog-body"}>
                    <DialogTitle id={"trusted-organization-request-dialog-title"}>Request organization trust</DialogTitle>
                    <DialogContent
                        id={"trusted-organization-request-dialog-content"}
                        className={styles.body}
                    >
                        <div
                            id={"trusted-organization-search-controls"}
                            className={styles.search}
                        >
                            <Input
                                id={"trusted-organization-search-input"}
                                aria-label={"Organization name or exact registration number"}
                                value={query}
                                placeholder={"Organization name"}
                                onChange={(_, data) => {
                                    searchSequence.current += 1;
                                    setQuery(data.value);
                                    setResults([]);
                                    setSelected(null);
                                }}
                            />
                            <Button
                                id={"trusted-organization-search-button"}
                                shape={"circular"}
                                appearance={"secondary"}
                                disabled={searching || query.trim().length < 3}
                                onClick={() => void search()}
                            >
                                Search
                            </Button>
                        </div>
                        {searching && (
                            <Spinner
                                id={"trusted-organization-search-spinner"}
                                size={"small"}
                            />
                        )}
                        {error && <div id={"trusted-organization-search-error"}>{error}</div>}
                        <OrganizationSearchResults
                            results={results}
                            selectedId={selected?.id}
                            onSelect={setSelected}
                        />
                        <Field
                            id={"trusted-organization-request-message-field"}
                            label={"Optional message"}
                        >
                            <Textarea
                                id={"trusted-organization-request-message"}
                                value={message}
                                resize={"vertical"}
                                onChange={(_, data) => setMessage(data.value)}
                            />
                        </Field>
                    </DialogContent>
                    <DialogActions id={"trusted-organization-request-dialog-actions"}>
                        <Button
                            id={"trusted-organization-send-request"}
                            shape={"circular"}
                            appearance={"primary"}
                            disabled={!selected || busy}
                            onClick={() => selected && void onSubmit(selected.id, message || undefined)}
                        >
                            Send request
                        </Button>
                        <Button
                            id={"trusted-organization-cancel-request"}
                            shape={"circular"}
                            appearance={"secondary"}
                            disabled={busy}
                            onClick={onDismiss}
                        >
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};
export default TrustedOrganizationRequestDialog;
