import React, {useState, useEffect, useRef} from "react";
import {
    Button,
    Combobox,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Option,
    Spinner, Text,
    Textarea
} from "@fluentui/react-components";
import {Dismiss12Regular} from "@fluentui/react-icons";
import {useParingRequestDialogStyles} from "./ParingRequestDialogStyles.tsx";
import {
    fetchOrganizationsForLinking,
    createOrganizationLink
} from "../../../../services/organizationSharingSession";
import {OrganizationBasicDto, OrganizationSharingSessionLinkBasicDto} from "../../../../app/models/models";
import {useAuth} from "../../../../context/AuthContext.tsx";

interface SessionDeleteDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    onRequestSent: (pair: OrganizationSharingSessionLinkBasicDto) => void;
}

const ParingRequestDialog: React.FC<SessionDeleteDialogProps> = (
    {
        isOpen,
        onDismiss,
        onRequestSent
    }) =>
{
    const {appUserPersonOrganization} = useAuth()
    const styles = useParingRequestDialogStyles();
    const [sendingParingRequests, setSendingParingRequests] = useState<boolean>(false);
    const [organizations, setOrganizations] = useState<OrganizationBasicDto[]>([]);
    const [selectedOrganizationIds, setSelectedOrganizationIds] = useState<string[]>([]);
    const [message, setMessage] = useState<string>("");
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    const extractErrorMessage = (e: any): string =>
    {
        if (typeof e === "string") return e;
        const msg = e?.errorMessage || e?.message || e?.response?.data?.errorMessage || e?.response?.data?.message;
        if (msg) return msg;
        return "Something went wrong. Please try again in a moment.";
    }

    const selectedListRef = useRef<HTMLUListElement>(null);
    const comboboxInputRef = useRef<HTMLInputElement>(null);

    useEffect(() =>
    {
        if (isOpen)
        {
            loadOrganizations();
        }
    }, [isOpen]);

    const loadOrganizations = async () =>
    {
        setIsLoading(true);
        setError(null);

        try
        {
            const orgs = await fetchOrganizationsForLinking();
            if (Array.isArray(orgs))
            {
                setOrganizations(orgs);
            }
        }
        catch (e)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to load organizations:", e);
        }
        setIsLoading(false);
    };

    const onPairSelectedOrgs = async () =>
    {
        if (selectedOrganizationIds.length === 0) return;

        setSendingParingRequests(true);
        setError(null);
        try
        {
            const pairingPromises = selectedOrganizationIds.map(orgId =>
                createOrganizationLink(appUserPersonOrganization?.id, orgId, message)
            );
            await Promise.all(pairingPromises);

            resetState()
            onDismiss();
            onRequestSent(null)
        }
        catch (e)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to create organization links:", e);
        }
        setSendingParingRequests(false);
    };

    const resetState = () =>
    {
        setSelectedOrganizationIds([])
        setMessage("")
        setError(null)
    }

    const onTagClick = (orgId: string, index: number) =>
    {
        // Remove selected organization
        setSelectedOrganizationIds(selectedOrganizationIds.filter(id => id !== orgId));

        // Focus previous or next option
        const indexToFocus = index === 0 ? 1 : index - 1;
        const optionToFocus = selectedListRef.current?.querySelector(
            `#org-remove-${indexToFocus}`
        );
        if (optionToFocus)
        {
            (optionToFocus as HTMLButtonElement).focus();
        }
        else
        {
            comboboxInputRef.current?.focus();
        }
    };

    const getOrgNameById = (orgId: string): string =>
    {
        const org = organizations.find(org => org.id === orgId);
        return org?.name || "Unknown organization";
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Find & Pair</DialogTitle>
                    <DialogContent className={styles.dialogContent}>
                        <div className={styles.errorContainer}>{error || " "}</div>

                        {isLoading &&
                            <Spinner size={"small"}/>
                        }
                        {(!isLoading && organizations.length == 0) &&
                            <Text> There are no organizations to pair with</Text>
                        }
                        {(!isLoading && organizations.length > 0) && <>
                            <Field className={styles.field}>
                                {selectedOrganizationIds.length > 0 && (
                                    <ul
                                        id="selected-orgs-list"
                                        className={styles.tagsList}
                                        ref={selectedListRef}>
                                    <span id="org-remove" hidden>
                                        Remove
                                    </span>
                                        {selectedOrganizationIds.map((orgId, i) => (
                                            <li key={orgId}>
                                                <Button
                                                    size="small"
                                                    shape="circular"
                                                    appearance="primary"
                                                    icon={<Dismiss12Regular/>}
                                                    iconPosition="after"
                                                    onClick={() => onTagClick(orgId, i)}
                                                    id={`org-remove-${i}`}
                                                    aria-labelledby={`org-remove org-remove-${i}`}>
                                                    {getOrgNameById(orgId)}
                                                </Button>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                                <Combobox
                                    multiselect={true}
                                    placeholder="Select organizations to pair with"
                                    selectedOptions={selectedOrganizationIds}
                                    onOptionSelect={(_, data) => setSelectedOrganizationIds(data.selectedOptions)}
                                    ref={comboboxInputRef}>
                                    {organizations.map((org) => (
                                        <Option key={org.id} value={org.id?.toString() || ""}>
                                            {org.name}
                                        </Option>
                                    ))}
                                </Combobox>
                            </Field>
                            <Field className={styles.field}
                                   label={`Message (optional) - ${message.length}/150 characters`}>
                                <Textarea
                                    placeholder="Add a message to the organizations you're pairing with"
                                    value={message}
                                    onChange={(e) => setMessage(e.target.value.slice(0, 100))}
                                    maxLength={100}
                                />
                            </Field>
                        </>
                        }
                    </DialogContent>
                    <DialogActions>
                        <Button
                            appearance="primary"
                            shape="circular"
                            className={styles.sendingRow}
                            onClick={onPairSelectedOrgs}
                            disabled={selectedOrganizationIds.length === 0 || sendingParingRequests}>
                            {sendingParingRequests && <Spinner size="tiny"/>}
                            Send Pair Requests
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                appearance="secondary"
                                shape="circular"
                                disabled={sendingParingRequests}
                                onClick={() =>
                                {
                                    resetState();
                                    onDismiss()
                                }}>
                                Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ParingRequestDialog;