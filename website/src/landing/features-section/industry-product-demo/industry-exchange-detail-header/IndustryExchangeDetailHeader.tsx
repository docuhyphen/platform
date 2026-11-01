import {Button} from "@fluentui/react-components";
import {
    bundleIcon,
    ChevronDownFilled,
    ChevronDownRegular,
    DocumentAddFilled,
    DocumentAddRegular,
    MoreVerticalRegular,
    PeopleLockFilled,
    PeopleLockRegular,
    WindowEditFilled,
    WindowEditRegular,
} from "@fluentui/react-icons";
import {useIndustryExchangeDetailHeaderStyles} from "./IndustryExchangeDetailHeaderStyles.tsx";

interface IndustryExchangeDetailHeaderProps
{
    title: string;
}

const DocumentAddIcon = bundleIcon(DocumentAddFilled, DocumentAddRegular);
const EditExchangeIcon = bundleIcon(WindowEditFilled, WindowEditRegular);
const ManageAccessIcon = bundleIcon(PeopleLockFilled, PeopleLockRegular);
const ToggleHeaderDownIcon = bundleIcon(ChevronDownFilled, ChevronDownRegular);

export function IndustryExchangeDetailHeader({title}: IndustryExchangeDetailHeaderProps)
{
    const styles = useIndustryExchangeDetailHeaderStyles();

    return (
        <header
            id="industry-demo-exchange-header"
            className={styles.header}
        >
            <span
                id="industry-demo-exchange-heading"
                className={styles.title}
            >
                {title}
            </span>
            <div
                id="industry-demo-exchange-actions"
                className={styles.actions}
            >
                <Button
                    id="industry-demo-add-document"
                    appearance="primary"
                    className={styles.wideActionButton}
                    shape="circular"
                    aria-label="Add Exchange Document"
                    icon={
                        <DocumentAddIcon
                            id="industry-demo-add-document-icon"
                            aria-hidden="true"
                        />
                    }
                />
                <Button
                    id="industry-demo-edit-exchange"
                    appearance="subtle"
                    className={styles.wideActionButton}
                    shape="circular"
                    aria-label="Edit"
                    icon={
                        <EditExchangeIcon
                            id="industry-demo-edit-exchange-icon"
                            aria-hidden="true"
                        />
                    }
                />
                <Button
                    id="industry-demo-manage-participants"
                    appearance="subtle"
                    className={styles.wideActionButton}
                    shape="circular"
                    aria-label="Manage access"
                    icon={
                        <ManageAccessIcon
                            id="industry-demo-manage-participants-icon"
                            aria-hidden="true"
                        />
                    }
                />
                <Button
                    id="industry-demo-more-actions"
                    appearance="subtle"
                    className={styles.actionButton}
                    shape="circular"
                    aria-label="More Exchange actions"
                    icon={
                        <MoreVerticalRegular
                            id="industry-demo-more-actions-icon"
                            aria-hidden="true"
                        />
                    }
                />
                <Button
                    id="industry-demo-collapse-exchange"
                    appearance="subtle"
                    className={styles.actionButton}
                    shape="circular"
                    size="small"
                    aria-label="Expand details"
                    icon={
                        <ToggleHeaderDownIcon
                            id="industry-demo-collapse-exchange-icon"
                            aria-hidden="true"
                        />
                    }
                />
            </div>
        </header>
    );
}
