import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Input,
    Spinner,
    Tab,
    TabList,
    Text,
} from "@fluentui/react-components";
import {SearchRegular} from "@fluentui/react-icons";
import {CommunicationScope, CommunicationSummaryDto} from "../../models/models.tsx";
import CommunicationPickerItem from "./communication-picker-item/CommunicationPickerItem.tsx";
import {useCommunicationChoices} from "./useCommunicationChoices.ts";
import {useCommunicationPickerDialogStyles} from "./CommunicationPickerDialogStyles.tsx";

interface Props
{
    open: boolean;
    onClose: () => void;
    onSelect: (communication: CommunicationSummaryDto) => void;
    selectedId?: string;
    fixedScope?: CommunicationScope;
    allowedScopes?: CommunicationScope[];
}

const ALL_SCOPES: CommunicationScope[] = ["PERSONAL", "ORG", "PLATFORM"];

const scopeLabels: Record<CommunicationScope, string> = {
    PERSONAL: "My Communications",
    ORG: "Organization",
    PLATFORM: "Platform",
};

const scopeTabIds: Record<CommunicationScope, string> = {
    PERSONAL: "communication-picker-personal-tab",
    ORG: "communication-picker-organization-tab",
    PLATFORM: "communication-picker-platform-tab",
};

const CommunicationPickerDialog = ({open, onClose, onSelect, selectedId, fixedScope, allowedScopes}: Props) =>
{
    const styles = useCommunicationPickerDialogStyles();
    const scopes = fixedScope ? [fixedScope] : allowedScopes ?? ALL_SCOPES;
    const choices = useCommunicationChoices(open, scopes);
    const {pending} = choices;

    const confirm = () =>
    {
        if (!pending) return;
        onSelect(pending);
        onClose();
    };

    return (
        <Dialog open={open}
                onOpenChange={(_, data) => !data.open && onClose()}>
            <DialogSurface id={"communication-picker-surface"}
                           aria-labelledby={"communication-picker-title"}
                           className={styles.dialogSurface}>
                <DialogBody id={"communication-picker-body"}>
                    <DialogTitle id={"communication-picker-title"}>Select Communication</DialogTitle>
                    <DialogContent id={"communication-picker-content"}
                                   className={styles.contentContainer}>
                        {scopes.length > 1 && (
                            <TabList id={"communication-picker-scope-tabs"}
                                     selectedValue={choices.activeTab}
                                     onTabSelect={(_, data) => choices.chooseScope(data.value)}>
                                {scopes.map(scope => (
                                    <Tab key={scope}
                                         id={scopeTabIds[scope]}
                                         value={scope}>
                                        {scopeLabels[scope]}
                                    </Tab>
                                ))}
                            </TabList>
                        )}
                        <Input id={"communication-picker-search-input"}
                               aria-label={"Search communications"}
                               contentBefore={<SearchRegular/>}
                               placeholder={"Search communications"}
                               value={choices.search}
                               onChange={(_, data) => choices.setSearch(data.value)}/>
                        {choices.loading && (
                            <Spinner id={"communication-picker-loading"}
                                     size={"small"}
                                     label={"Loading communications"}/>
                        )}
                        {!choices.loading && choices.filtered.length === 0 && (
                            <Text id={"communication-picker-empty"}
                                  className={styles.noResults}>
                                No communications found.
                            </Text>
                        )}
                        <div id={"communication-picker-items"}
                             className={styles.itemList}>
                            {!choices.loading && choices.filtered.map(communication => (
                                <CommunicationPickerItem key={communication.id}
                                                         communication={communication}
                                                         selected={pending ? pending.id === communication.id : selectedId === communication.id}
                                                         onSelect={() => choices.setPending(communication)}/>
                            ))}
                        </div>
                    </DialogContent>
                    <DialogActions id={"communication-picker-actions"}>
                        <Button id={"communication-picker-confirm-btn"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={!pending}
                                onClick={confirm}>
                            Select Communication
                        </Button>
                        <Button id={"communication-picker-cancel-btn"}
                                appearance={"secondary"}
                                shape={"circular"}
                                onClick={onClose}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default CommunicationPickerDialog;
